package net.agolyakov.firelamp.data.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import net.agolyakov.firelamp.ui.screen.demo.demoState
import java.io.File

class FirmwareModelsTest {
    @Test
    fun parsesTheBundledFirmware() {
        val file = File("src/main/assets/demo/firmware-0.11.1.json")
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val firmware = FirmwareModelsRepository.parse(file.readText())

        assertEquals("0.11.1", firmware.info.firmwareVersion)
        assertEquals(45, firmware.info.effectCount)
        assertEquals(45, firmware.effects.size)
        assertEquals(138, firmware.effects.sumOf { it.params.size })
        val fire = firmware.effects.first { it.code == "fire" }
        assertEquals("Turbin fire", fire.name)
        assertEquals(fire.params.size, fire.params.map { it.code }.distinct().size)

        val state = firmware.demoState()
        assertEquals(45, state.effects.size)
        assertEquals("fire", state.lampState.effectCode)
        assertEquals(5, state.bonds.capacity)
        assertEquals(2, state.bonds.phones.size)
        assertEquals(state.info.matrixWidth, state.led.width)
        assertEquals(2000, state.power.limitMa)
    }
}
