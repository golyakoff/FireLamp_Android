package net.agolyakov.firelamp.service.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

/**
 * The WiFi network the phone is connected to, to prefill the lamp's WiFi settings when the
 * lamp's firmware cannot scan itself. Android reveals the SSID only with the location permission
 * granted and location turned on; otherwise this returns null.
 */
object PhoneWifi {
    data class Network(val ssid: String, val frequencyMhz: Int) {
        /** The lamp (ESP32 / ESP32-C3) works in the 2.4 GHz band only. */
        val is24GHz: Boolean get() = frequencyMhz in 2400..2500
    }

    /** Android asks for both together; the SSID needs the fine one. */
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun current(context: Context): Network? {
        if (!hasPermission(context)) return null
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return try {
            // Deprecated for apps that manage networks, still the way to read the current SSID
            @Suppress("DEPRECATION")
            val info = wifi.connectionInfo ?: return null
            // "<unknown ssid>" without the permission or location (WifiManager.UNKNOWN_SSID, API 30+)
            val ssid = info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
                ?: return null
            Network(ssid, info.frequency)
        } catch (e: SecurityException) {
            null
        }
    }
}
