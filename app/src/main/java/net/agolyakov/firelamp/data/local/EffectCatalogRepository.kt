package net.agolyakov.firelamp.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import net.agolyakov.firelamp.protocol.DeviceInfo
import net.agolyakov.firelamp.protocol.EffectInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Effect descriptions per lamp, so a connection does not have to enumerate the whole list
 * (two GATT operations per effect). The descriptions do not change while the firmware build is
 * the same, except the current values and the demo flags, which [BluetoothService] refreshes.
 *
 * One file per lamp (keyed by MAC), holding the firmware build it was read from; a different
 * build invalidates it.
 */
@Singleton
class EffectCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val dir: File get() = File(context.filesDir, "effects").apply { mkdirs() }

    private data class Entry(val build: String, val effects: List<EffectInfo>)

    fun load(macAddress: String, info: DeviceInfo): List<EffectInfo>? = try {
        val file = file(macAddress)
        if (!file.exists()) {
            null
        } else {
            val entry: Entry = gson.fromJson(file.readText(), object : TypeToken<Entry>() {}.type)
            entry.effects.takeIf { entry.build == build(info) && it.size == info.effectCount }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the effect cache", e)
        null
    }

    fun save(macAddress: String, info: DeviceInfo, effects: List<EffectInfo>) {
        try {
            file(macAddress).writeText(gson.toJson(Entry(build(info), effects)))
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the effect cache", e)
        }
    }

    private fun build(info: DeviceInfo) = "${info.firmwareVersion}|${info.buildDate}|${info.buildVariant}"

    private fun file(macAddress: String) = File(dir, macAddress.replace(":", "") + ".json")

    private companion object {
        const val TAG = "EffectCatalog"
    }
}
