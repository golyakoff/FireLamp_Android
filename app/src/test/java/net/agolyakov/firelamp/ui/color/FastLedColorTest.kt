package net.agolyakov.firelamp.ui.color

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reference values of FastLED's `hsv2rgb_rainbow` (K255/K171/K170/K85 at the section starts). */
class FastLedColorTest {

    private fun hex(hue: Int, sat: Int = 255, value: Int = 255) = "%06X".format(FastLedColor.hsvToRgb(hue, sat, value))

    @Test
    fun primaries() {
        assertEquals("FF0000", hex(0))      // red
        assertEquals("AB5500", hex(32))     // orange
        assertEquals("ABAA00", hex(64))     // yellow
        assertEquals("00FF00", hex(96))     // green
        assertEquals("00AB55", hex(128))    // aqua
        assertEquals("0000FF", hex(160))    // blue
        assertEquals("5500AB", hex(192))    // purple
        assertEquals("AA0055", hex(224))    // pink
    }

    @Test
    fun saturationAndValue() {
        assertEquals("FFFFFF", hex(100, sat = 0))
        assertEquals("000000", hex(100, value = 0))
        // Half saturation lifts the floor
        assertEquals("FF4040", hex(0, sat = 128))
    }
}
