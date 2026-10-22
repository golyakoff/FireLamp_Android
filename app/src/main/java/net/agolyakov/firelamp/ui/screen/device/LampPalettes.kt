package net.agolyakov.firelamp.ui.screen.device

import androidx.annotation.StringRes
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.ui.color.FastLedColor

/** A palette of the lamp for a preview tile: its name and colors (`0xRRGGBB`), blended in order. */
data class LampPalette(@StringRes val name: Int, val colors: List<Int>)

/** Copies of the firmware's palettes (`main/palettes.c` and a few effect-local ones). */
object LampPalettes {

    /** `palettes[]` of palettes.c, by its index (`PALETTE_FIRE` = 0 ...). */
    val SHARED = listOf(
        LampPalette(R.string.palette_fire, listOf(0x000000, 0xFF0000, 0xFFFF00, 0xFFFFFF)),
        LampPalette(R.string.palette_wood_fire, listOf(0x000000, 0xFF4500, 0xFFA500, 0xFFD700)),
        LampPalette(R.string.palette_sodium_fire, listOf(0x000000, 0xFFA500, 0xFFD700, 0xDAA520)),
        LampPalette(R.string.palette_copper_fire, listOf(0x000000, 0x008000, 0xADFF2F, 0x32CD32)),
        LampPalette(R.string.palette_alcohol_fire, listOf(0x000000, 0x0000FF, 0x00BFFF, 0x87CEFA)),
        LampPalette(R.string.palette_rubidium_fire, listOf(0x000000, 0x4B0082, 0x4B0082, 0x00008B)),
        LampPalette(R.string.palette_potassium_fire, listOf(0x000000, 0x4B0082, 0x9370DB, 0xFF1493)),
        LampPalette(R.string.palette_lithium_fire, listOf(0x000000, 0xB22222, 0xFFC0CB, 0xFF1493)),
        LampPalette(
            R.string.palette_cloud, listOf(
                0x0000FF, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B,
                0x0000FF, 0x00008B, 0x87CEEB, 0x87CEEB, 0xADD8E6, 0xFFFFFF, 0xADD8E6, 0x87CEEB,
            )
        ),
        LampPalette(
            R.string.palette_lava, listOf(
                0x000000, 0x800000, 0x000000, 0x800000, 0x8B0000, 0x800000, 0x8B0000, 0x800000,
                0x8B0000, 0x8B0000, 0xFF0000, 0xFFA500, 0xFFFFFF, 0xFFA500, 0xFF0000, 0x8B0000,
            )
        ),
        LampPalette(
            R.string.palette_heat, listOf(
                0x000000, 0x330000, 0x660000, 0x990000, 0xFF0000, 0xFF3300, 0xFF6600,
                0xFF9900, 0xFFFF00, 0xFFFF33, 0xFFFF66, 0xFFFF99, 0xFFFFFF,
            )
        ),
        LampPalette(
            R.string.palette_ocean, listOf(
                0x191970, 0x00008B, 0x191970, 0x000080, 0x00008B, 0x0000CD, 0x2E8B57, 0x008080,
                0x5F9EA0, 0x0000FF, 0x008B8B, 0x6495ED, 0x7FFFD4, 0x2E8B57, 0x00FFFF, 0x87CEFA,
            )
        ),
        LampPalette(
            R.string.palette_forest, listOf(
                0x006400, 0x006400, 0x556B2F, 0x006400, 0x008000, 0x228B22, 0x6B8E23, 0x008000,
                0x2E8B57, 0x66CDAA, 0x32CD32, 0x9ACD32, 0x90EE90, 0x7CFC00, 0x66CDAA, 0x228B22,
            )
        ),
        LampPalette(
            R.string.palette_party, listOf(
                0xFF0000, 0x000000, 0xAB5500, 0x000000, 0xABAB00, 0x000000, 0x008000, 0x000000,
                0x00AB55, 0x000000, 0x0000FF, 0x000000, 0x5500AB, 0x000000, 0xAB0055, 0x000000,
            )
        ),
    )

    /** All hues (twinkles' last palette value). */
    val RAINBOW = LampPalette(R.string.palette_rainbow, (0 until 256 step 16).map { FastLedColor.hsvToRgb(it, 255) })

    /** metaballs.c `_palettes`. */
    val METABALLS = listOf(
        LampPalette(R.string.palette_fire, listOf(0x000000, 0x000000, 0xFF0000, 0xFFFF00, 0xFFFF00, 0xFFFFFF)),
        LampPalette(R.string.palette_blue, listOf(0x000000, 0x000000, 0x00008B, 0x00FFFF, 0x00FFFF, 0xFFFFFF)),
        LampPalette(R.string.palette_green, listOf(0x000000, 0x000000, 0x006400, 0x00FF7F, 0x00FF7F, 0xFFFFFF)),
    )

    /** polar_lights.c: black, blue or green, yellow, red. */
    val POLAR_LIGHTS = listOf(
        LampPalette(R.string.palette_blue, listOf(0x000000, 0x0000FF, 0xFFFF00, 0xFF0000)),
        LampPalette(R.string.palette_green, listOf(0x000000, 0x00FF00, 0xFFFF00, 0xFF0000)),
    )
}
