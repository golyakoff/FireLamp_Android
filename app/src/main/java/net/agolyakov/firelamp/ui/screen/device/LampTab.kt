package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.EffectParam
import net.agolyakov.firelamp.protocol.LampState
import net.agolyakov.firelamp.protocol.ParamType
import net.agolyakov.firelamp.ui.component.SyncedSlider
import net.agolyakov.firelamp.ui.theme.FireLampTheme

/** Localized name of an effect, or the English one from the lamp for an unknown code. */
@Composable
fun effectName(effect: EffectInfo): String =
    EffectNames.effect(effect.code)?.let { stringResource(it) } ?: effect.name

@Composable
private fun paramName(param: EffectParam): String =
    EffectNames.param(param.code)?.let { stringResource(it) } ?: param.name

@Composable
fun LampTab(
    lampState: LampState,
    effects: List<EffectInfo>,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onSelectEffect: (String) -> Unit,
    onNextEffect: (forward: Boolean) -> Unit,
    onDemoChange: (Boolean) -> Unit,
    onParamChange: (effectCode: String, paramCode: String, value: Int) -> Unit,
) {
    val current = effects.firstOrNull { it.code == lampState.effectCode }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PowerCard(lampState, onPowerChange, onBrightnessChange)
        }
        item {
            CurrentEffectCard(
                lampState = lampState,
                effect = current,
                onNextEffect = onNextEffect,
                onDemoChange = onDemoChange,
                onParamChange = onParamChange
            )
        }
        item {
            EffectList(effects, lampState.effectCode, onSelectEffect)
        }
    }
}

@Composable
private fun PowerCard(
    lampState: LampState,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (lampState.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(if (lampState.on) R.string.lamp_on else R.string.lamp_off),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = lampState.on, onCheckedChange = onPowerChange)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.BrightnessMedium,
                    contentDescription = stringResource(R.string.lamp_brightness),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(12.dp))
                SyncedSlider(
                    value = lampState.brightness,
                    range = 1..255,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier.weight(1f),
                    valueLabel = { ValueLabel("${(it * 100 + 254) / 255}%") }
                )
            }
        }
    }
}

@Composable
private fun CurrentEffectCard(
    lampState: LampState,
    effect: EffectInfo?,
    onNextEffect: (Boolean) -> Unit,
    onDemoChange: (Boolean) -> Unit,
    onParamChange: (String, String, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNextEffect(false) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.lamp_previous_effect))
                }
                Text(
                    text = effect?.let { effectName(it) } ?: lampState.effectCode,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onNextEffect(true) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.lamp_next_effect))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.lamp_demo), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.lamp_demo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = lampState.demo, onCheckedChange = onDemoChange)
            }

            if (effect != null && effect.params.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                effect.params.forEach { param ->
                    ParamRow(param) { onParamChange(effect.code, param.code, it) }
                }
            }
        }
    }
}

@Composable
private fun ParamRow(param: EffectParam, onChange: (Int) -> Unit) {
    when (param.type) {
        ParamType.BOOL -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(paramName(param), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = param.value != 0, onCheckedChange = { onChange(if (it) 1 else 0) })
        }
        ParamType.RANGE -> Column {
            Text(paramName(param), style = MaterialTheme.typography.bodyMedium)
            SyncedSlider(
                value = param.value,
                range = param.min..param.max,
                onValueChange = onChange,
                valueLabel = { value ->
                    if (isHue(param)) HueSwatch(value) else ValueLabel(value.toString())
                }
            )
        }
    }
}

/** Hue parameters are 0..255 around the color wheel (FastLED hue). */
private fun isHue(param: EffectParam) =
    (param.code == "hue" || param.code.startsWith("hue_") || param.code.endsWith("_hue")) &&
        param.min == 0 && param.max == 255

@Composable
private fun HueSwatch(hue: Int) {
    Box(
        modifier = Modifier
            .padding(start = 12.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.hsv(hue * 360f / 256f, 1f, 1f))
    )
}

@Composable
private fun ValueLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.End,
        modifier = Modifier.width(48.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectList(
    effects: List<EffectInfo>,
    currentCode: String,
    onSelectEffect: (String) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.lamp_effects),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            effects.forEach { effect ->
                FilterChip(
                    selected = effect.code == currentCode,
                    onClick = { onSelectEffect(effect.code) },
                    label = { Text(effectName(effect)) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LampTabPreview() {
    val effects = listOf(
        EffectInfo(
            0, 3, "fire", "Fire", true, listOf(
                EffectParam("palette", "Palette", ParamType.RANGE, 0, 5, 0, 2),
                EffectParam("scale", "Scale", ParamType.RANGE, 1, 255, 60, 100),
            )
        ),
        EffectInfo(
            1, 3, "rain", "Rain", true, listOf(
                EffectParam("colored", "Colored", ParamType.BOOL, 0, 1, 0, 1),
                EffectParam("hue", "Hue", ParamType.RANGE, 0, 255, 150, 150),
            )
        ),
        EffectInfo(2, 3, "new_one", "Some new effect", false, emptyList()),
    )
    FireLampTheme {
        LampTab(
            lampState = LampState(on = true, brightness = 128, fps = 60, demo = false, effectCode = "rain"),
            effects = effects,
            onPowerChange = {},
            onBrightnessChange = {},
            onSelectEffect = {},
            onNextEffect = {},
            onDemoChange = {},
            onParamChange = { _, _, _ -> },
        )
    }
}
