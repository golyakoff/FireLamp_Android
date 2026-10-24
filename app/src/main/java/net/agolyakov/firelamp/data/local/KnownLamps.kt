package net.agolyakov.firelamp.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import net.agolyakov.firelamp.protocol.DeviceInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MAC addresses of lamps this app has been connected to. A bonded lamp held connected by the
 * system does not advertise, so the Home screen lists bonded devices too: by the default name
 * `FireLamp-*`, or by this list for lamps that were given another name.
 *
 * Kept apart from [FireLampPreferences], whose file holds one entry per device and is read back
 * as a whole.
 */
@Singleton
class KnownLamps @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("known_lamps", Context.MODE_PRIVATE)
    private val builds = context.getSharedPreferences("lamp_builds", Context.MODE_PRIVATE)

    fun add(macAddress: String) {
        if (!contains(macAddress)) prefs.edit { putBoolean(macAddress, true) }
    }

    fun contains(macAddress: String): Boolean = prefs.contains(macAddress)

    /** The firmware build ([DeviceInfo.build]) whose attribute table was discovered last time. */
    fun discoveredBuild(macAddress: String): String? = builds.getString(macAddress, null)

    fun setDiscoveredBuild(macAddress: String, build: String) {
        if (discoveredBuild(macAddress) != build) builds.edit { putString(macAddress, build) }
    }
}
