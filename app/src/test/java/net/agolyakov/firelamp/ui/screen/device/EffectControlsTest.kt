package net.agolyakov.firelamp.ui.screen.device

import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.EffectParam
import net.agolyakov.firelamp.protocol.ParamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parameter sets as the firmware declares them (DECL_PARAM_* in main/effects/). */
class EffectControlsTest {

    private fun range(code: String, min: Int, max: Int, value: Int = min) =
        EffectParam(code, code, ParamType.RANGE, min, max, min, value)

    private fun effect(code: String, vararg params: EffectParam) = EffectInfo(0, 1, code, code, true, params.toList())

    private fun kinds(effect: EffectInfo) = EffectControls.of(effect).map { it::class.simpleName }

    @Test
    fun rgbBecomesOneColor() {
        val lamp = effect("lamp", range("red", 0, 255), range("green", 0, 255), range("blue", 0, 255))
        assertEquals(listOf("Rgb"), kinds(lamp))
    }

    @Test
    fun hueWithSaturation() {
        val wheel = effect(
            "wheel", range("speed", 1, 255), range("hue_1", 0, 255), range("saturation_1", 0, 255),
            range("hue_2", 0, 255), range("saturation_2", 0, 255)
        )
        assertEquals(listOf("Tempo", "HueSaturation", "HueSaturation"), kinds(wheel))
        // relax: start_hue pairs with the only saturation
        val relax = effect("relax", range("start_hue", 0, 255), range("saturation", 0, 255))
        assertEquals(listOf("HueSaturation"), kinds(relax))
    }

    @Test
    fun loneHueAndLoneSaturation() {
        assertEquals(listOf("Hue"), kinds(effect("waterfall", range("color", 0, 255))))
        assertEquals(listOf("Plain"), kinds(effect("peacock", range("saturation", 0, 255))))
    }

    @Test
    fun palettesByEffect() {
        val fire = EffectControls.of(effect("fire", range("palette", 0, 7))).single() as ParamControl.Palette
        assertEquals(8, fire.palettes.size)
        val twinkles = EffectControls.of(effect("twinkles", range("palette", 0, 14))).single() as ParamControl.Palette
        assertEquals(LampPalettes.RAINBOW, twinkles.palettes.last())
        val metaballs = EffectControls.of(effect("metaballs", range("palette", 0, 2))).single() as ParamControl.Palette
        assertEquals(LampPalettes.METABALLS, metaballs.palettes)
        // More palettes than the app knows (newer firmware): a plain slider
        assertEquals(listOf("Plain"), kinds(effect("dunes", range("palette", 0, 20))))
    }

    @Test
    fun modes() {
        assertEquals(listOf("Choice"), kinds(effect("holiday", range("mode", 0, 3))))
        assertEquals(listOf("Plain"), kinds(effect("holiday", range("mode", 0, 4))))
        assertEquals(listOf("Plain"), kinds(effect("unknown", range("mode", 0, 2))))
    }

    @Test
    fun tempo() {
        // the common tempo and the effects' own speeds are one kind of control, in percent
        assertEquals(listOf("Tempo"), kinds(effect("starfall", range("tempo", 10, 100, value = 30))))
        assertEquals(listOf("Tempo"), kinds(effect("lava", range("speed", 0, 50))))
        assertEquals(listOf("Plain"), kinds(effect("dna", range("rotation_speed", 10, 100))))
        assertEquals(30, EffectControls.tempoPercent(range("tempo", 10, 100, value = 30)))
        assertEquals(10, EffectControls.tempoPercent(range("speed", 0, 50, value = 5)))
        assertEquals(30, EffectControls.tempoPercent(range("speed", 1, 10, value = 3)))
        assertEquals(100, EffectControls.tempoPercent(range("speed", 1, 255, value = 255)))
        // moving, but less than half a percent: shown as 1 %, stopped: 0 %
        assertEquals(1, EffectControls.tempoPercent(range("speed", 1, 255, value = 1)))
        assertEquals(0, EffectControls.tempoPercent(range("speed", 0, 54, value = 0)))
    }

    @Test
    fun dependentParams() {
        val palette = range("palette", 0, 13)
        assertFalse(EffectControls.isActive(effect("lava", range("mode", 0, 2, value = 1), palette), palette))
        assertTrue(EffectControls.isActive(effect("lava", range("mode", 0, 2, value = 2), palette), palette))
    }
}
