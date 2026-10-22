package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import net.agolyakov.firelamp.ui.color.ColorButton
import net.agolyakov.firelamp.ui.color.HueSaturationDialog
import net.agolyakov.firelamp.ui.color.LampRainbow
import net.agolyakov.firelamp.ui.color.RgbColorDialog
import net.agolyakov.firelamp.ui.color.lampHueColor
import net.agolyakov.firelamp.ui.color.rgbColor
import net.agolyakov.firelamp.ui.component.SliderRow
import net.agolyakov.firelamp.ui.component.SliderValue
import net.agolyakov.firelamp.ui.component.SliderValueWidth
import net.agolyakov.firelamp.ui.component.SwitchRow
import net.agolyakov.firelamp.ui.component.brightnessPercent
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
    onFpsChange: (Int) -> Unit = {},
    /** The demo settings sheet, opened from the demo switch; gets its dismiss callback. */
    demoSettingsSheet: @Composable (onDismiss: () -> Unit) -> Unit = {},
) {
    val current = effects.firstOrNull { it.code == lampState.effectCode }
    var demoSettingsOpen by rememberSaveable { mutableStateOf(false) }
    if (demoSettingsOpen) demoSettingsSheet { demoSettingsOpen = false }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PowerCard(lampState, onPowerChange, onBrightnessChange, onFpsChange)
        }
        // The lamp is off: effects stay at hand, dimmed
        val offAlpha = if (lampState.on) 1f else 0.6f
        item {
            Box(Modifier.alpha(offAlpha)) {
                CurrentEffectCard(
                    lampState = lampState,
                    effect = current,
                    onNextEffect = onNextEffect,
                    onDemoChange = onDemoChange,
                    onDemoSettings = { demoSettingsOpen = true },
                    onParamChange = onParamChange
                )
            }
        }
        item {
            Box(Modifier.alpha(offAlpha)) {
                EffectList(effects, lampState.effectCode, onSelectEffect)
            }
        }
    }
}

@Composable
private fun PowerCard(
    lampState: LampState,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onFpsChange: (Int) -> Unit,
) {
    var more by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            SliderRow(
                label = stringResource(R.string.lamp_brightness),
                value = lampState.brightness,
                range = 1..255,
                onValueChange = onBrightnessChange,
                valueLabel = { SliderValue(brightnessPercent(it)) }
            )
            // Rarely changed: folded away
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { more = !more }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.lamp_more),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (more) {
                SliderRow(
                    label = stringResource(R.string.settings_fps),
                    value = lampState.fps,
                    range = 1..120,
                    onValueChange = onFpsChange,
                    valueLabel = { SliderValue(stringResource(R.string.unit_fps, it)) }
                )
                Text(
                    stringResource(R.string.settings_fps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
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
    onDemoSettings: () -> Unit,
    onParamChange: (String, String, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNextEffect(false) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.lamp_previous_effect))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // What the lamp shows, playing while it is on (follows ◀ ▶ and the demo)
                    EffectPreview(lampState.effectCode, playing = lampState.on, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = effect?.let { effectName(it) } ?: lampState.effectCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
                IconButton(onClick = { onNextEffect(true) }) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.lamp_next_effect))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.lamp_demo), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.lamp_demo_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDemoSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.lamp_demo_settings))
                }
                Switch(checked = lampState.demo, onCheckedChange = onDemoChange)
            }

            if (effect != null && effect.params.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EffectControls.of(effect).forEach { control ->
                        ParamControlRow(effect, control) { paramCode, value -> onParamChange(effect.code, paramCode, value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamControlRow(effect: EffectInfo, control: ParamControl, onChange: (paramCode: String, value: Int) -> Unit) {
    val active = control.params.all { EffectControls.isActive(effect, it) }
    when (control) {
        is ParamControl.Plain -> {
            val param = control.param
            when (param.type) {
                ParamType.BOOL -> SwitchRow(
                    label = paramName(param),
                    checked = param.value != 0,
                    enabled = active,
                    onCheckedChange = { onChange(param.code, if (it) 1 else 0) }
                )
                ParamType.RANGE -> SliderRow(
                    label = paramName(param),
                    value = param.value,
                    range = param.min..param.max,
                    enabled = active,
                    onValueChange = { onChange(param.code, it) }
                )
            }
        }
        is ParamControl.Tempo -> SliderRow(
            label = paramName(control.param),
            value = control.param.value,
            range = control.param.min..control.param.max,
            enabled = active,
            onValueChange = { onChange(control.param.code, it) },
            valueLabel = { SliderValue("${EffectControls.tempoPercent(control.param, it)}%") }
        )
        is ParamControl.Hue -> SliderRow(
            label = paramName(control.param),
            value = control.param.value,
            range = 0..255,
            enabled = active,
            onValueChange = { onChange(control.param.code, it) },
            valueLabel = { HueSwatch(lampHueColor(it)) },
            trackBrush = LampRainbow
        )
        is ParamControl.HueSaturation -> {
            var open by remember { mutableStateOf(false) }
            val label = control.label?.let { stringResource(it) } ?: paramName(control.hue)
            ColorRow(label, lampHueColor(control.hue.value, control.saturation.value), active) { open = true }
            if (open) {
                HueSaturationDialog(
                    title = label,
                    hue = control.hue.value,
                    saturation = control.saturation.value,
                    onChange = { h, sat ->
                        onChange(control.hue.code, h)
                        onChange(control.saturation.code, sat)
                    },
                    onDismiss = { open = false }
                )
            }
        }
        is ParamControl.Rgb -> {
            var open by remember { mutableStateOf(false) }
            val label = stringResource(R.string.color_hue)
            val rgb = (control.red.value shl 16) or (control.green.value shl 8) or control.blue.value
            ColorRow(label, rgbColor(rgb), active) { open = true }
            if (open) {
                RgbColorDialog(
                    title = label,
                    red = control.red.value,
                    green = control.green.value,
                    blue = control.blue.value,
                    onChange = { r, g, b ->
                        onChange(control.red.code, r)
                        onChange(control.green.code, g)
                        onChange(control.blue.code, b)
                    },
                    onDismiss = { open = false }
                )
            }
        }
        is ParamControl.Palette -> PaletteRow(paramName(control.param), control, active) { onChange(control.param.code, it) }
        is ParamControl.Choice -> ChoiceChips(paramName(control.param), control, active) { onChange(control.param.code, it) }
    }
}

/** A label and a color sample that opens the picker. */
@Composable
private fun ColorRow(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
        ColorButton(color, enabled, onClick)
    }
}

/** Palette tiles in a row, each a gradient of the palette's colors with its name. */
@Composable
private fun PaletteRow(label: String, control: ParamControl.Palette, enabled: Boolean, onSelect: (Int) -> Unit) {
    val selected = control.param.value - control.param.min
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(control.palettes) { index, palette ->
                PaletteTile(palette, selected = index == selected, enabled = enabled) { onSelect(control.param.min + index) }
            }
        }
    }
}

@Composable
private fun PaletteTile(palette: LampPalette, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(palette.colors.map { rgbColor(it) }))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                )
        )
        Text(
            stringResource(palette.name),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Named mode values as chips (labels may be too long for segmented buttons). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(label: String, control: ParamControl.Choice, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            control.labels.forEachIndexed { index, labelRes ->
                val value = control.param.min + index
                FilterChip(
                    selected = control.param.value == value,
                    enabled = enabled,
                    onClick = { onSelect(value) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }
    }
}

@Composable
private fun HueSwatch(color: Color) {
    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.width(SliderValueWidth)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Effects as preview tiles: a tap switches the lamp to the effect, and its tile plays while the
 * others show their first frame. As many columns as fit (at least 3).
 */
@Composable
private fun EffectList(
    effects: List<EffectInfo>,
    currentCode: String,
    onSelectEffect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lamp_effects), style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints {
            val columns = (maxWidth / TILE_MIN_WIDTH).toInt().coerceAtLeast(3)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                effects.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { effect ->
                            EffectTile(
                                effect = effect,
                                selected = effect.code == currentCode,
                                onClick = { onSelectEffect(effect.code) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keep the last row's tiles the same width
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private val TILE_MIN_WIDTH = 88.dp

@Composable
private fun EffectTile(effect: EffectInfo, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        EffectPreview(
            code = effect.code,
            playing = selected,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape
                )
        )
        Text(
            effectName(effect),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
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
