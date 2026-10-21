package net.agolyakov.firelamp.ui.screen.device

import androidx.annotation.StringRes
import net.agolyakov.firelamp.R

/**
 * Localized names of effects and their parameters, by code. A code the app does not know
 * (an effect added by a newer firmware) is shown with the English name sent by the lamp.
 */
object EffectNames {
    @StringRes
    fun effect(code: String): Int? = effects[code]

    @StringRes
    fun param(code: String): Int? = params[code]

    private val effects = mapOf(
        "lamp" to R.string.effect_lamp,
        "fire" to R.string.effect_fire,
        "sparkles" to R.string.effect_sparkles,
        "lava" to R.string.effect_lava,
        "waterfall" to R.string.effect_waterfall,
        "plasma_waves" to R.string.effect_plasma_waves,
        "dna" to R.string.effect_dna,
        "matrix" to R.string.effect_matrix,
        "crazybees" to R.string.effect_crazybees,
        "rain" to R.string.effect_rain,
        "rainbow" to R.string.effect_rainbow,
        "rays" to R.string.effect_rays,
        "metaballs" to R.string.effect_metaballs,
        "water" to R.string.effect_water,
        "holiday" to R.string.effect_holiday,
        "hoop" to R.string.effect_hoop,
        "twister" to R.string.effect_twister,
        "flower" to R.string.effect_flower,
        "polar_lights" to R.string.effect_polar_lights,
        "gif" to R.string.effect_gif,
        "relax" to R.string.effect_relax,
        "wheel" to R.string.effect_wheel,
        "peacock" to R.string.effect_peacock,
        "diagonals" to R.string.effect_diagonals,
        "whirls" to R.string.effect_whirls,
        "sun" to R.string.effect_sun,
        "spaceships" to R.string.effect_spaceships,
        "fireflies" to R.string.effect_fireflies,
        "tunnel" to R.string.effect_tunnel,
        "dunes" to R.string.effect_dunes,
        "octopus" to R.string.effect_octopus,
        "nuclear" to R.string.effect_nuclear,
    )

    private val params = mapOf(
        "red" to R.string.param_red,
        "green" to R.string.param_green,
        "blue" to R.string.param_blue,
        "palette" to R.string.param_palette,
        "scale" to R.string.param_scale,
        "sparkles" to R.string.param_sparkles,
        "fadeout" to R.string.param_fadeout,
        "speed" to R.string.param_speed,
        "mode" to R.string.param_mode,
        "color" to R.string.param_color,
        "cooling" to R.string.param_cooling,
        "sparking" to R.string.param_sparking,
        "rotation_speed" to R.string.param_rotation_speed,
        "spiral_size" to R.string.param_spiral_size,
        "white_border" to R.string.param_white_border,
        "density" to R.string.param_density,
        "bees" to R.string.param_bees,
        "bee_hue" to R.string.param_bee_hue,
        "bee_saturation" to R.string.param_bee_saturation,
        "colored" to R.string.param_colored,
        "hue" to R.string.param_hue,
        "tail_length" to R.string.param_tail_length,
        "direction" to R.string.param_direction,
        "min_rays" to R.string.param_min_rays,
        "max_rays" to R.string.param_max_rays,
        "balls" to R.string.param_balls,
        "saturation" to R.string.param_saturation,
        "black_border" to R.string.param_black_border,
        "iridescent" to R.string.param_iridescent,
        "diameter" to R.string.param_diameter,
        "height" to R.string.param_height,
        "file" to R.string.param_file,
        "start_hue" to R.string.param_start_hue,
        "pixels" to R.string.param_pixels,
        "hue_1" to R.string.param_hue_1,
        "saturation_1" to R.string.param_saturation_1,
        "hue_2" to R.string.param_hue_2,
        "saturation_2" to R.string.param_saturation_2,
        "rays" to R.string.param_rays,
        "count" to R.string.param_count,
        "time_scale" to R.string.param_time_scale,
        "size" to R.string.param_size,
        "ships" to R.string.param_ships,
        "turn_time" to R.string.param_turn_time,
        "fireflies" to R.string.param_fireflies,
        "chaotic" to R.string.param_chaotic,
        "update_time" to R.string.param_update_time,
        "legs" to R.string.param_legs,
        "clockwise" to R.string.param_clockwise,
    )
}
