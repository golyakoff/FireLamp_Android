package net.agolyakov.firelamp.data.extensions

import android.bluetooth.le.ScanResult
import net.agolyakov.firelamp.data.model.ble.FireLampDevice

const val UNNAMED = "<unnamed>"

fun ScanResult.toBleDevice(): FireLampDevice {
    return FireLampDevice(
        deviceName = this.scanRecord?.deviceName ?: UNNAMED,
        macAddress = this.device.address
    )
}
