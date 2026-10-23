package net.agolyakov.firelamp.ui.screen.device

import androidx.annotation.StringRes
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.EffectParam
import net.agolyakov.firelamp.protocol.ParamType

/**
 * How the parameters of an effect are shown: the lamp sends plain numbers, the app knows which
 * of them are colors, palettes and modes (by code, from the firmware's effects and palettes.c).
 * A parameter that does not match the tables (other range, newer firmware) stays a plain slider.
 */
sealed interface ParamControl {
    /** A slider or a switch. */
    data class Plain(val param: EffectParam) : ParamControl

    /**
     * The tempo of an effect: `tempo` (`10..100` %, firmware ≥ 0.5.0) or a former `speed` with its
     * own range. Both are shown as percent of the maximum, see [EffectControls.tempoPercent].
     */
    data class Tempo(val param: EffectParam) : ParamControl

    /** A lone hue `0..255`: one color picked on the ring of the color wheel. */
    data class Hue(val param: EffectParam) : ParamControl

    /** A hue and its saturation: one color picked on the color wheel. */
    data class HueSaturation(@StringRes val label: Int?, val hue: EffectParam, val saturation: EffectParam) : ParamControl

    /** Red, green and blue `0..255`: one color picked on the color wheel. */
    data class Rgb(val red: EffectParam, val green: EffectParam, val blue: EffectParam) : ParamControl

    /** A palette number: tiles, [palettes] by value from the parameter's minimum. */
    data class Palette(val param: EffectParam, val palettes: List<LampPalette>) : ParamControl

    /** A mode number: named options, [labels] by value from the parameter's minimum. */
    data class Choice(val param: EffectParam, @StringRes val labels: List<Int>) : ParamControl

    val params: List<EffectParam>
        get() = when (this) {
            is Plain -> listOf(param)
            is Tempo -> listOf(param)
            is Hue -> listOf(param)
            is HueSaturation -> listOf(hue, saturation)
            is Rgb -> listOf(red, green, blue)
            is Palette -> listOf(param)
            is Choice -> listOf(param)
        }
}

object EffectControls {

    /** The controls of an effect, in the order of their first parameter. */
    fun of(effect: EffectInfo): List<ParamControl> {
        val params = effect.params
        val byCode = params.associateBy { it.code }
        val used = mutableSetOf<String>()
        val controls = mutableListOf<ParamControl>()

        fun byteRange(p: EffectParam?) = p != null && p.type == ParamType.RANGE && p.min == 0 && p.max == 255

        for (param in params) {
            if (param.code in used) continue
            val control: ParamControl = when {
                param.code in RGB && RGB.all { byteRange(byCode[it]) } ->
                    ParamControl.Rgb(byCode.getValue("red"), byCode.getValue("green"), byCode.getValue("blue"))
                isHueCode(param.code) && byteRange(param) -> {
                    val saturation = saturationFor(param.code, params)?.takeIf { byteRange(it) && it.code !in used }
                    if (saturation != null) {
                        ParamControl.HueSaturation(COLOR_LABELS[param.code], param, saturation)
                    } else {
                        ParamControl.Hue(param)
                    }
                }
                param.code in TEMPO && param.type == ParamType.RANGE && param.max > 0 ->
                    ParamControl.Tempo(param)
                param.code == "palette" && param.type == ParamType.RANGE ->
                    palettesFor(effect.code).takeIf { param.min >= 0 && param.max < it.size }
                        ?.let { ParamControl.Palette(param, it.subList(param.min, param.max + 1)) }
                        ?: ParamControl.Plain(param)
                else -> CHOICES[effect.code to param.code]
                    ?.takeIf { param.type == ParamType.RANGE && param.min == 0 && param.max == it.size - 1 }
                    ?.let { ParamControl.Choice(param, it) }
                    ?: ParamControl.Plain(param)
            }
            control.params.forEach { used += it.code }
            controls += control
        }
        return controls
    }

    /**
     * A tempo value as percent of the parameter's maximum: `tempo` is percent already, a former
     * `speed` (e.g. Lava `0..50`, Dunes `1..10`) gets the same scale. Never 0 % while it moves.
     */
    fun tempoPercent(param: EffectParam, value: Int = param.value): Int {
        if (param.max <= 0 || value <= 0) return 0
        return ((value * 100 + param.max / 2) / param.max).coerceIn(1, 100)
    }

    /**
     * Whether a parameter matters with the current values of the others (e.g. the lava palette
     * only in the palette mode). One that does not is shown dimmed.
     */
    fun isActive(effect: EffectInfo, param: EffectParam): Boolean {
        val rule = ACTIVE_WHEN[effect.code to param.code] ?: return true
        val other = effect.params.firstOrNull { it.code == rule.first } ?: return true
        return other.value in rule.second
    }

    private val RGB = listOf("red", "green", "blue")

    // the effect's own speed parameter or the common tempo: an effect has one of them
    private val TEMPO = setOf("tempo", "speed")

    private fun isHueCode(code: String) =
        code == "hue" || code == "color" || code.startsWith("hue_") || code.endsWith("_hue")

    /** `bee_hue` -> `bee_saturation`, `hue_1` -> `saturation_1`; `start_hue` (relax) -> `saturation`. */
    private fun saturationFor(hueCode: String, params: List<EffectParam>): EffectParam? {
        if ("hue" !in hueCode) return null // "color" (waterfall) has no saturation
        val exact = hueCode.replace("hue", "saturation")
        params.firstOrNull { it.code == exact }?.let { return it }
        // A single hue with a single saturation of another name
        val hues = params.count { isHueCode(it.code) }
        return if (hues == 1) params.firstOrNull { it.code == "saturation" } else null
    }

    private val COLOR_LABELS = mapOf(
        "hue" to R.string.color_hue,
        "bee_hue" to R.string.color_bee_hue,
        "hue_1" to R.string.color_hue_1,
        "hue_2" to R.string.color_hue_2,
        "start_hue" to R.string.color_start_hue,
    )

    /** Named values of mode parameters, from the firmware's enums (holiday.h, lava.c, ...). */
    private val CHOICES: Map<Pair<String, String>, List<Int>> = mapOf(
        ("holiday" to "mode") to listOf(R.string.mode_holiday_wave, R.string.mode_holiday_radar, R.string.mode_holiday_sparks, R.string.mode_holiday_tree),
        ("lava" to "mode") to listOf(R.string.mode_rainbow, R.string.mode_lava, R.string.mode_palette),
        ("glass_drops" to "mode") to listOf(R.string.mode_color, R.string.mode_rainbow, R.string.mode_gray),
        ("waterfall" to "mode") to listOf(R.string.mode_colors, R.string.mode_fire, R.string.mode_cold_fire),
        ("rainbow" to "direction") to listOf(R.string.direction_horizontal, R.string.direction_vertical, R.string.direction_diagonal),
    )

    /** Parameter -> (other parameter, its values in which the first one is used). */
    private val ACTIVE_WHEN: Map<Pair<String, String>, Pair<String, IntRange>> = mapOf(
        ("lava" to "palette") to ("mode" to 2..2),
        ("glass_drops" to "hue") to ("mode" to 0..0),
        ("waterfall" to "color") to ("mode" to 0..0),
    )

    private fun palettesFor(effectCode: String): List<LampPalette> = when (effectCode) {
        "metaballs" -> LampPalettes.METABALLS
        "polar_lights" -> LampPalettes.POLAR_LIGHTS
        // The value after the shared ones means all hues
        "twinkles" -> LampPalettes.SHARED + LampPalettes.RAINBOW
        else -> LampPalettes.SHARED
    }
}
