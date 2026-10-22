package net.agolyakov.firelamp.ui.screen.device

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.Schedule
import net.agolyakov.firelamp.protocol.ScheduleTime
import net.agolyakov.firelamp.protocol.TimeSource
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.component.SwitchRow
import net.agolyakov.firelamp.ui.component.SliderRow
import net.agolyakov.firelamp.ui.component.SliderValue
import net.agolyakov.firelamp.ui.component.brightnessPercent
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Power on/off, dawn and hourly brightness. The schedule is one characteristic, read and written
 * as a whole, so it is edited as a draft with Save / Discard at the bottom.
 */
@Composable
fun ScheduleTab(
    schedule: Schedule,
    effects: List<EffectInfo>,
    lampTime: LampTime?,
    onSave: (Schedule) -> Unit,
) {
    // Saveable: the draft survives switching tabs (DeviceScreen keeps each tab's saved state)
    var draft by rememberSaveable(schedule) { mutableStateOf(schedule) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Schedules run only when the lamp knows the time
            if (lampTime != null && !lampTime.valid) {
                item { TimeNotSetCard() }
            }
            item {
                SettingsSection(stringResource(R.string.schedule_power_on)) {
                    ScheduleTimeEditor(draft.powerOn) { draft = draft.copy(powerOn = it) }
                }
            }
            item {
                SettingsSection(stringResource(R.string.schedule_power_off)) {
                    ScheduleTimeEditor(draft.powerOff) { draft = draft.copy(powerOff = it) }
                }
            }
            item { DawnSection(draft, effects) { draft = it } }
            item { HourlySection(draft) { draft = it } }
        }

        if (draft != schedule) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedButton(onClick = { draft = schedule }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.discard))
                }
                Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun TimeNotSetCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.schedule_time_not_set),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/** Enabled, time and days of one schedule entry. */
@Composable
private fun ScheduleTimeEditor(
    time: ScheduleTime,
    timeLabel: String = stringResource(R.string.schedule_at),
    onChange: (ScheduleTime) -> Unit,
) {
    SwitchRow(
        label = stringResource(R.string.schedule_enabled),
        checked = time.enabled,
        onCheckedChange = { onChange(time.copy(enabled = it)) }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(timeLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TimeButton(time.hour, time.minute, enabled = time.enabled) { h, m -> onChange(time.copy(hour = h, minute = m)) }
    }
    DaysSelector(time.days, enabled = time.enabled) { onChange(time.copy(days = it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(hour: Int, minute: Int, enabled: Boolean, onPick: (Int, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled) {
        Text(clockText(hour, minute), style = MaterialTheme.typography.titleMedium)
    }
    if (open) {
        val state = rememberTimePickerState(hour, minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(state.hour, state.minute)
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            text = { TimePicker(state = state) }
        )
    }
}

/** A time of day in the phone's 12/24-hour format, as the time picker shows it. */
@Composable
private fun clockText(hour: Int, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** Monday first, as the bits go: bit 0 - Monday ... bit 6 - Sunday. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysSelector(days: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { bit, day ->
            val on = days and (1 shl bit) != 0
            FilterChip(
                selected = on,
                enabled = enabled,
                onClick = { onChange(days xor (1 shl bit)) },
                label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) }
            )
        }
    }
}

@Composable
private fun DawnSection(draft: Schedule, effects: List<EffectInfo>, onChange: (Schedule) -> Unit) {
    SettingsSection(stringResource(R.string.schedule_dawn)) {
        Text(
            stringResource(R.string.schedule_dawn_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        ScheduleTimeEditor(draft.dawn, timeLabel = stringResource(R.string.schedule_dawn_at)) {
            onChange(draft.copy(dawn = it))
        }
        val enabled = draft.dawn.enabled
        SliderRow(
            label = stringResource(R.string.schedule_dawn_duration),
            value = draft.dawnDurationMinutes,
            range = 1..120,
            onValueChange = { onChange(draft.copy(dawnDurationMinutes = it)) },
            enabled = enabled,
            valueLabel = { SliderValue(stringResource(R.string.unit_minutes, it)) }
        )
        SliderRow(
            label = stringResource(R.string.schedule_dawn_brightness),
            value = draft.dawnBrightness,
            range = 1..255,
            onValueChange = { onChange(draft.copy(dawnBrightness = it)) },
            enabled = enabled,
            valueLabel = { SliderValue(brightnessPercent(it)) }
        )
        EffectDropdown(
            label = stringResource(R.string.schedule_dawn_effect),
            effects = effects,
            selectedCode = draft.dawnEffectCode,
            enabled = enabled
        ) { onChange(draft.copy(dawnEffectCode = it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectDropdown(
    label: String,
    effects: List<EffectInfo>,
    selectedCode: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = effects.firstOrNull { it.code == selectedCode }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected?.let { effectName(it) } ?: selectedCode,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            effects.forEach { effect ->
                DropdownMenuItem(
                    text = { Text(effectName(effect)) },
                    onClick = {
                        onSelect(effect.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HourlySection(draft: Schedule, onChange: (Schedule) -> Unit) {
    var hour by rememberSaveable { mutableIntStateOf(LocalTime.now().hour) }
    SettingsSection(stringResource(R.string.schedule_hourly)) {
        SwitchRow(
            label = stringResource(R.string.schedule_enabled),
            hint = stringResource(R.string.schedule_hourly_hint),
            checked = draft.hourlyBrightnessEnabled,
            onCheckedChange = { onChange(draft.copy(hourlyBrightnessEnabled = it)) }
        )
        HourBars(draft.hourlyBrightness, hour, enabled = draft.hourlyBrightnessEnabled) { hour = it }
        SliderRow(
            label = stringResource(R.string.schedule_hourly_at, clockText(hour, 0)),
            value = draft.hourlyBrightness[hour],
            range = 1..255,
            enabled = draft.hourlyBrightnessEnabled,
            onValueChange = { v ->
                onChange(draft.copy(hourlyBrightness = draft.hourlyBrightness.toMutableList().also { it[hour] = v }))
            },
            valueLabel = { SliderValue(brightnessPercent(it)) }
        )
    }
}

/** 24 bars, one per hour; tap a bar to edit that hour with the slider below. */
@Composable
private fun HourBars(values: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val active = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.tertiary
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onSelect((offset.x / size.width * 24).toInt().coerceIn(0, 23)) }
                }
        ) {
            val slot = size.width / 24
            val bar = slot * 0.7f
            for (h in 0 until 24) {
                val x = h * slot + (slot - bar) / 2
                drawRoundRect(inactive, Offset(x, 0f), Size(bar, size.height), CornerRadius(3f))
                val height = size.height * values[h] / 255f
                drawRoundRect(
                    if (h == selected) highlight else active,
                    Offset(x, size.height - height),
                    Size(bar, height),
                    CornerRadius(3f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(0, 6, 12, 18).forEach {
                Text("$it", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun ScheduleTabPreview() {
    FireLampTheme {
        ScheduleTab(
            schedule = Schedule(
                powerOn = ScheduleTime(true, ScheduleTime.WORKDAYS, 7, 0),
                powerOff = ScheduleTime(true, ScheduleTime.EVERY_DAY, 23, 30),
                dawn = ScheduleTime(false, ScheduleTime.WORKDAYS, 6, 45),
                dawnDurationMinutes = 30,
                dawnBrightness = 200,
                hourlyBrightnessEnabled = true,
                hourlyBrightness = List(24) { if (it in 8..21) 200 else 40 },
                dawnEffectCode = "sun",
            ),
            effects = listOf(EffectInfo(0, 1, "sun", "Sun radiation", true, emptyList())),
            lampTime = LampTime(0, valid = false, source = TimeSource.NONE),
            onSave = {},
        )
    }
}
