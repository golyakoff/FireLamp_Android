package net.agolyakov.firelamp.data.model.firmware

import com.google.gson.Gson
import net.agolyakov.firelamp.protocol.Chip
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmwareManifestTest {

    // The example of APP_INTEGRATION.md, section 9
    private val json = """
        {
          "version": "0.1.0",
          "builds": {
            "esp32-default":   { "url": "https://github.com/x/firelamp_v0.1.0_esp32_fw.bin",   "size": 1398064, "sha256": "aa" },
            "esp32c3-default": { "url": "https://github.com/x/firelamp_v0.1.0_esp32c3_fw.bin", "size": 1538240, "sha256": "bb" }
          }
        }
    """.trimIndent()

    private fun lamp(chip: Chip, variant: String) =
        DeviceInfo(1, chip, 4, 0, 16, 16, 32, Controls.ONE_BUTTON, "0.0.9", "", variant)

    @Test
    fun parsesAndPicksTheBuild() {
        val manifest = Gson().fromJson(json, FirmwareManifest::class.java)
        assertEquals("0.1.0", manifest.version)
        val build = manifest.buildFor(lamp(Chip.ESP32_C3, "default"))!!
        assertEquals(1538240L, build.size)
        assertEquals("bb", build.sha256)
        assertTrue(build.url.endsWith("esp32c3_fw.bin"))
    }

    @Test
    fun noOfficialBuildForACustomVariant() {
        val manifest = Gson().fromJson(json, FirmwareManifest::class.java)
        assertNull(manifest.buildFor(lamp(Chip.ESP32, "custom_pins")))
        assertNull(manifest.buildFor(lamp(Chip.UNKNOWN, "default")))
    }

    @Test
    fun versions() {
        assertTrue(FirmwareVersion.isNewer("0.1.10", "0.1.9"))
        assertTrue(FirmwareVersion.isNewer("1.0.0", "0.99.99"))
        assertTrue(FirmwareVersion.isNewer("v0.2.0", "0.1.1"))
        assertFalse(FirmwareVersion.isNewer("0.1.1", "0.1.1"))
        assertFalse(FirmwareVersion.isNewer("0.1.0", "0.1.1"))
        assertEquals(0, FirmwareVersion.compare("0.1", "0.1.0"))
        assertNull(FirmwareVersion.compare("dev", "0.1.0"))
        assertFalse(FirmwareVersion.isNewer("dev", "0.1.0"))
    }
}
