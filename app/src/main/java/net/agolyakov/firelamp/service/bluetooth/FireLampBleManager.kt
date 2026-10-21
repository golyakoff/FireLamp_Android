package net.agolyakov.firelamp.service.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.agolyakov.firelamp.protocol.FireLampGatt
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import java.util.UUID

/**
 * The GATT session with one lamp: a thin, format-agnostic layer over Nordic's [BleManager].
 * It moves raw bytes by characteristic UUID; what the bytes mean is `protocol.FireLampCodec`'s
 * business, and what to do with them is [BluetoothService]'s.
 *
 * Initialization (run by the library before the device is "ready"): MTU 517, bonding when the
 * phone is not bonded yet, notifications on.
 */
class FireLampBleManager(context: Context) : BleManager(context) {

    private val tag = "FireLampBleManager"

    /** A notification: which characteristic and its value. */
    data class Notification(val uuid: UUID, val value: ByteArray)

    private val _notifications = MutableSharedFlow<Notification>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val notifications: SharedFlow<Notification> = _notifications.asSharedFlow()

    private var characteristics: Map<UUID, BluetoothGattCharacteristic> = emptyMap()

    /** Negotiated MTU of the current connection; the OTA chunk size is `mtu - 3`. */
    val negotiatedMtu: Int get() = mtu

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(FireLampGatt.SERVICE) ?: return false
        characteristics = FireLampGatt.ALL.mapNotNull { uuid -> service.getCharacteristic(uuid)?.let { uuid to it } }.toMap()
        val missing = FireLampGatt.ALL - characteristics.keys
        if (missing.isNotEmpty()) Log.w(tag, "Missing characteristics: $missing")
        return missing.isEmpty()
    }

    override fun onServicesInvalidated() {
        characteristics = emptyMap()
    }

    override fun initialize() {
        requestMtu(FireLampGatt.MTU)
            .fail { _, status -> Log.w(tag, "MTU request failed, status=$status") }
            .enqueue()
        // Every characteristic needs an encrypted link from a bonded phone. Without a bond the
        // first access would fail with Insufficient Encryption and Android would start pairing
        // by itself, but an explicit bond is easier to follow (and to fail clearly).
        // createBondInsecure completes at once when the phone is already bonded.
        if (bluetoothDevice?.bondState != BluetoothDevice.BOND_BONDED) {
            createBondInsecure().enqueue()
        }
        for (uuid in FireLampGatt.NOTIFYING) {
            val characteristic = characteristics.getValue(uuid)
            setNotificationCallback(characteristic).with { _, data ->
                data.value?.let { _notifications.tryEmit(Notification(uuid, it)) }
            }
            enableNotifications(characteristic).enqueue()
        }
    }

    private fun characteristic(uuid: UUID): BluetoothGattCharacteristic =
        characteristics[uuid] ?: throw IllegalStateException("Not connected (no characteristic $uuid)")

    /** Reads a characteristic; throws on a GATT error (e.g. ATT error codes from the lamp). */
    suspend fun read(uuid: UUID): ByteArray =
        readCharacteristic(characteristic(uuid)).suspend().value ?: ByteArray(0)

    /** Writes a characteristic with response; throws on a GATT error. */
    suspend fun write(uuid: UUID, value: ByteArray) {
        writeCharacteristic(characteristic(uuid), value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).suspend()
    }

    override fun log(priority: Int, message: String) {
        if (priority >= Log.INFO) Log.println(priority, tag, message)
    }
}
