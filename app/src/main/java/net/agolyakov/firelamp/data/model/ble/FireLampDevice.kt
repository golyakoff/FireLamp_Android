package net.agolyakov.firelamp.data.model.ble

import java.io.Serializable

/**
 * A lamp in the Home list; handed to the Device screen through the back stack's SavedStateHandle,
 * which takes Serializable values (no Parcelize: it needs a compiler plugin).
 */
data class FireLampDevice(
    val deviceName: String,             // Device name assigned by the manufacturer
    val macAddress: String,             // MAC address
    val friendlyName: String? = null,   // Optional user-assigned name
    val inRange: Boolean = true,        // Found by scanning; false - connected to the phone, not advertising
) : Serializable {
    fun getDisplayName(): String {
        return friendlyName ?: deviceName
    }
}
