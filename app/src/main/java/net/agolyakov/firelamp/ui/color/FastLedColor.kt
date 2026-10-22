package net.agolyakov.firelamp.ui.color

/**
 * The lamp's own HSV to RGB conversion (FastLED `hsv2rgb_rainbow`, used by the firmware's color
 * library), so a hue the app shows looks like the one the lamp lights: its "rainbow" gives yellow
 * and orange more room than the plain spectrum of `android.graphics.Color.HSVToColor`.
 *
 * Pure integer math, as in FastLED; all values `0..255`, the result is `0xRRGGBB`.
 */
object FastLedColor {

    fun hsvToRgb(hue: Int, saturation: Int, value: Int = 255): Int {
        val h = hue and 0xFF
        val offset8 = (h and 0x1F) shl 3
        val third = scale8(offset8, 256 / 3) // max 85
        val twoThirds = scale8(offset8, 256 * 2 / 3) // max 170
        var r: Int
        var g: Int
        var b: Int
        when (h shr 5) {
            0 -> { r = 255 - third; g = third; b = 0 }          // red -> orange
            1 -> { r = 171; g = 85 + third; b = 0 }             // orange -> yellow
            2 -> { r = 171 - twoThirds; g = 170 + third; b = 0 } // yellow -> green
            3 -> { r = 0; g = 255 - third; b = third }          // green -> aqua
            4 -> { r = 0; g = 171 - twoThirds; b = 85 + twoThirds } // aqua -> blue
            5 -> { r = third; g = 0; b = 255 - third }          // blue -> purple
            6 -> { r = 85 + third; g = 0; b = 171 - third }     // purple -> pink
            else -> { r = 170 + third; g = 0; b = 85 - third }  // pink -> red
        }

        val s = saturation.coerceIn(0, 255)
        if (s != 255) {
            if (s == 0) {
                r = 255; g = 255; b = 255
            } else {
                val desat = scale8Video(255 - s, 255 - s)
                val satScale = 255 - desat
                r = scale8(r, satScale) + desat
                g = scale8(g, satScale) + desat
                b = scale8(b, satScale) + desat
            }
        }

        var v = value.coerceIn(0, 255)
        if (v != 255) {
            v = scale8Video(v, v)
            r = scale8(r, v); g = scale8(g, v); b = scale8(b, v)
        }
        return (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** `i * s / 256`, where s = 255 keeps i (FastLED's fixed `scale8`). */
    private fun scale8(i: Int, s: Int): Int = (i * (1 + s)) shr 8

    /** Like [scale8], but never scales a non-zero value to zero. */
    private fun scale8Video(i: Int, s: Int): Int = ((i * s) shr 8) + if (i != 0 && s != 0) 1 else 0
}
