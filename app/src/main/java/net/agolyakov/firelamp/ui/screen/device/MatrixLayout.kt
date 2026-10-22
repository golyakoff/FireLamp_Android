package net.agolyakov.firelamp.ui.screen.device

import net.agolyakov.firelamp.protocol.Rotation
import net.agolyakov.firelamp.protocol.Wiring

/**
 * How the LED strip runs across the picture, for the orientation diagram: the firmware's
 * `build_led_map()` (surface.c) inverted. The picture is what the effects draw, upright; its
 * width and height swap with a 90/270° rotation.
 */
object MatrixLayout {

    /** A picture cell: x from the left, y from the bottom. */
    data class Cell(val x: Int, val y: Int)

    /** The picture size for a matrix of [width] × [height] LEDs. */
    fun pictureSize(width: Int, height: Int, rotation: Rotation): Pair<Int, Int> =
        if (rotation == Rotation.CW_90 || rotation == Rotation.CW_270) height to width else width to height

    /** Picture cells in the order of the LEDs on the strip: the first one is the strip's start. */
    fun stripPath(width: Int, height: Int, rotation: Rotation, mirror: Boolean, wiring: Wiring): List<Cell> {
        val (fbWidth, fbHeight) = pictureSize(width, height, rotation)
        val byLed = arrayOfNulls<Cell>(width * height)
        for (y in 0 until fbHeight) {
            for (x in 0 until fbWidth) {
                var (px, py) = when (rotation) {
                    Rotation.CW_90 -> y to fbWidth - 1 - x
                    Rotation.CW_180 -> fbWidth - 1 - x to fbHeight - 1 - y
                    Rotation.CW_270 -> fbHeight - 1 - y to x
                    Rotation.NONE -> x to y
                }
                if (mirror) px = width - 1 - px
                val reversed = wiring == Wiring.SERPENTINE && py % 2 == 1
                byLed[py * width + if (reversed) width - px - 1 else px] = Cell(x, y)
            }
        }
        return byLed.map { requireNotNull(it) }
    }
}
