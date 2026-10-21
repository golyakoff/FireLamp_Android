package net.agolyakov.firelamp.data.model.firmware

import com.google.gson.annotations.SerializedName
import net.agolyakov.firelamp.protocol.DeviceInfo

/**
 * `manifest.json` of a FireLamp_ESP32 GitHub release: the version and one firmware file per
 * build key `<chip>-<variant>` (e.g. `esp32c3-default`), see `APP_INTEGRATION.md`, section 9.
 */
data class FirmwareManifest(
    @SerializedName("version") val version: String,
    @SerializedName("builds") val builds: Map<String, FirmwareBuild>,
) {
    /**
     * The file for this lamp, or null when the release has none for its chip and build variant:
     * a lamp with a custom pinout has no official firmware and must not be offered one.
     */
    fun buildFor(info: DeviceInfo): FirmwareBuild? = info.firmwareBuildKey?.let { builds[it] }
}

data class FirmwareBuild(
    @SerializedName("url") val url: String,
    @SerializedName("size") val size: Long,
    /** 64 hex digits. */
    @SerializedName("sha256") val sha256: String,
)

/** `MAJOR.MINOR.PATCH` versions compared numerically: `0.1.10` is newer than `0.1.9`. */
object FirmwareVersion {
    /** Negative, zero or positive like [Comparable.compareTo]; null when either is not a version. */
    fun compare(a: String, b: String): Int? {
        val x = parse(a) ?: return null
        val y = parse(b) ?: return null
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = (compare(candidate, current) ?: 0) > 0

    /** `v0.2.1` / `0.2.1-rc1` -> [0, 2, 1]. */
    private fun parse(version: String): List<Int>? =
        version.trim().trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
}
