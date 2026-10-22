package net.agolyakov.firelamp.ui.screen.device

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every effect and parameter of the firmware has a localized name. The codes are those of
 * FireLamp_ESP32 0.4.3 (main/effect.c and DECL_PARAM_* in main/effects/): add the codes of new
 * effects here together with their names in effects.xml (values and values-ru).
 */
class EffectNamesTest {

    private val effects = """
        lamp fire sparkles lava waterfall plasma_waves dna matrix crazybees rain rainbow rays
        metaballs water holiday hoop twister flower polar_lights gif relax wheel peacock diagonals
        whirls sun spaceships fireflies tunnel dunes octopus nuclear starfall storm twinkles
        butterflies rainbow_snake soaring_fire trail_fireflies glass_drops
    """.trim().split(Regex("\\s+"))

    private val params = """
        balls bee_hue bee_saturation bees black_border blue butterflies chaotic clockwise clouds
        color colored cooling count density diameter direction drops fadeout file fireflies green
        height hue hue_1 hue_2 iridescent lamp legs lightning max_rays min_rays mode palette pixels
        rays red rotate rotation_speed saturation saturation_1 saturation_2 scale ships size
        sparking sparkles speed spiral_size start_hue tail_length time_scale turn_time update_time
        white_border wings
    """.trim().split(Regex("\\s+"))

    @Test
    fun allEffectsNamed() {
        val missing = effects.filter { EffectNames.effect(it) == null }
        assertTrue("effects without a name: $missing", missing.isEmpty())
    }

    @Test
    fun allParamsNamed() {
        val missing = params.filter { EffectNames.param(it) == null }
        assertTrue("parameters without a name: $missing", missing.isEmpty())
    }
}
