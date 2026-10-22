package net.agolyakov.firelamp.ui.screen.device

import net.agolyakov.firelamp.protocol.Rotation
import net.agolyakov.firelamp.protocol.Wiring
import net.agolyakov.firelamp.ui.screen.device.MatrixLayout.Cell
import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixLayoutTest {

    private fun path(rotation: Rotation = Rotation.NONE, mirror: Boolean = false, wiring: Wiring = Wiring.SERPENTINE) =
        MatrixLayout.stripPath(3, 2, rotation, mirror, wiring)

    @Test
    fun plainLayouts() {
        // From the bottom left along the rows
        assertEquals(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1), Cell(1, 1), Cell(0, 1)), path())
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1)),
            path(wiring = Wiring.PROGRESSIVE)
        )
    }

    @Test
    fun mirrorStartsAtTheRight() {
        assertEquals(Cell(2, 0), path(mirror = true).first())
    }

    @Test
    fun rotationSwapsThePicture() {
        assertEquals(2 to 3, MatrixLayout.pictureSize(3, 2, Rotation.CW_90))
        val rotated = path(Rotation.CW_90)
        assertEquals(6, rotated.toSet().size)
        assertEquals(true, rotated.all { it.x in 0..1 && it.y in 0..2 })
        // 180°: the strip starts at the top right of the picture
        assertEquals(Cell(2, 1), path(Rotation.CW_180).first())
    }
}
