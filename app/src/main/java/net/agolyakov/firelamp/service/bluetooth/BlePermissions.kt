package net.agolyakov.firelamp.service.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions for BLE. The user can revoke them in the system settings at any time,
 * and every Bluetooth call then throws a SecurityException: check before scanning/connecting.
 */
object BlePermissions {
    val required: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 and older: scanning needs the location permission
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Whether the phone is bonded with the device; false also when the permission was revoked. */
fun BluetoothDevice.isBonded(): Boolean = try {
    bondState == BluetoothDevice.BOND_BONDED
} catch (e: SecurityException) {
    false
}
