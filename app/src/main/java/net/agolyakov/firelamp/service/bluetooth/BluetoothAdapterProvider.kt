package net.agolyakov.firelamp.service.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.annotation.RequiresPermission
import javax.inject.Inject

class BluetoothAdapterProvider @Inject constructor(
    private val context: Context
) {
    private val manager: BluetoothManager
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    fun getAdapter(): BluetoothAdapter = manager.adapter

    /** Devices with a GATT link to this phone, opened by any app or by the system. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getConnectedGattDevices(): List<BluetoothDevice> = manager.getConnectedDevices(BluetoothProfile.GATT)

    fun getContext(): Context {
        return context
    }
}