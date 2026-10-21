package net.agolyakov.firelamp.data.model.ble

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FireLampDevice(
    val deviceName: String,             // Device name assigned by the manufacturer
    val macAddress: String,             // MAC address
    val friendlyName: String? = null,   // Optional user-assigned name
    val inRange: Boolean = true,        // Found by scanning; false - connected to the phone, not advertising
) : Parcelable {
    fun getDisplayName(): String {
        return friendlyName ?: deviceName
    }
}
