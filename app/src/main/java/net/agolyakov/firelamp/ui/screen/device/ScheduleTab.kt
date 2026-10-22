package net.agolyakov.firelamp.ui.screen.device

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import net.agolyakov.firelamp.ui.component.SliderRow
import net.agolyakov.firelamp.ui.component.SliderValue
import net.agolyakov.firelamp.ui.component.brightnessPercent
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

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
                ScheduleCard(
                    title = stringResource(R.string.schedule_power_on),
                    enabled = draft.powerOn.enabled,
                    onEnabledChange = { draft = draft.copy(powerOn = draft.powerOn.copy(enabled = it)) },
                    summary = timeSummary(draft.powerOn)
                ) {
                    ScheduleTimeEditor(draft.powerOn) { draft = draft.copy(powerOn = it) }
                }
            }
            item {
                ScheduleCard(
                    title = stringResource(R.string.schedule_power_off),
                    enabled = draft.powerOff.enabled,
                    onEnabledChange = { draft = draft.copy(powerOff = draft.powerOff.copy(enabled = it)) },
                    summary = timeSummary(draft.powerOff)
                ) {
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

/**
 * A schedule entry: the title with its switch and a one-line summary; the settings unfold only
 * while it is on.
 */
@Composable
private fun ScheduleCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    summary: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) summary ?: stringResource(R.string.schedule_on) else stringResource(R.string.schedule_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

/** "Weekdays, 07:00". */
@Composable
private fun timeSummary(time: ScheduleTime): String =
    stringResource(R.string.schedule_summary, daysText(time.days), clockText(time.hour, time.minute))

@Composable
private fun daysText(days: Int): String = when (days and ScheduleTime.EVERY_DAY) {
    ScheduleTime.EVERY_DAY -> stringResource(R.string.schedule_every_day)
    ScheduleTime.WORKDAYS -> stringResource(R.string.schedule_workdays)
    WEEKEND -> stringResource(R.string.schedule_weekend)
    0 -> stringResource(R.string.schedule_no_days)
    else -> DayOfWeek.entries.filterIndexed { bit, _ -> days and (1 shl bit) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()) }
}

/** Saturday and Sunday (bits 5, 6). */
private const val WEEKEND = 0x60

/** Time and days of one schedule entry. */
@Composable
private fun ScheduleTimeEditor(
    time: ScheduleTime,
    timeLabel: String = stringResource(R.string.schedule_at),
    onChange: (ScheduleTime) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(timeLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TimeButton(time.hour, time.minute, enabled = true) { h, m -> onChange(time.copy(hour = h, minute = m)) }
    }
    DaysSelector(time.days) { onChange(time.copy(days = it)) }
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
private fun DaysSelector(days: Int, onChange: (Int) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { bit, day ->
            val on = days and (1 shl bit) != 0
            FilterChip(
                selected = on,
                onClick = { onChange(days xor (1 shl bit)) },
                label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) }
            )
        }
    }
    // Quick sets
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ScheduleTime.WORKDAYS to R.string.schedule_workdays,
            WEEKEND to R.string.schedule_weekend,
            ScheduleTime.EVERY_DAY to R.string.schedule_every_day,
        ).forEach { (set, label) ->
            SuggestionChip(onClick = { onChange(set) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun DawnSection(draft: Schedule, effects: List<EffectInfo>, onChange: (Schedule) -> Unit) {
    ScheduleCard(
        title = stringResource(R.string.schedule_dawn),
        enabled = draft.dawn.enabled,
        onEnabledChange = { onChange(draft.copy(dawn = draft.dawn.copy(enabled = it))) },
        summary = timeSummary(draft.dawn)
    ) {
        Text(
            stringResource(R.string.schedule_dawn_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        ScheduleTimeEditor(draft.dawn, timeLabel = stringResource(R.string.schedule_dawn_at)) {
            onChange(draft.copy(dawn = it))
        }
        SliderRow(
            label = stringResource(R.string.schedule_dawn_duration),
            value = draft.dawnDurationMinutes,
            range = 1..120,
            onValueChange = { onChange(draft.copy(dawnDurationMinutes = it)) },
            valueLabel = { SliderValue(stringResource(R.string.unit_minutes, it)) }
        )
        SliderRow(
            label = stringResource(R.string.schedule_dawn_brightness),
            value = draft.dawnBrightness,
            range = 1..255,
            onValueChange = { onChange(draft.copy(dawnBrightness = it)) },
            valueLabel = { SliderValue(brightnessPercent(it)) }
        )
        EffectDropdown(
            label = stringResource(R.string.schedule_dawn_effect),
            effects = effects,
            selectedCode = draft.dawnEffectCode,
            enabled = true
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
    fun setHour(h: Int, v: Int) {
        onChange(draft.copy(hourlyBrightness = draft.hourlyBrightness.toMutableList().also { it[h] = v }))
    }
    ScheduleCard(
        title = stringResource(R.string.schedule_hourly),
        enabled = draft.hourlyBrightnessEnabled,
        onEnabledChange = { onChange(draft.copy(hourlyBrightnessEnabled = it)) },
        summary = null
    ) {
        Text(
            stringResource(R.string.schedule_hourly_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        HourBars(
            values = draft.hourlyBrightness,
            selected = hour,
            onSelect = { hour = it },
            onPaint = { h, v ->
                hour = h
                setHour(h, v)
            }
        )
        SliderRow(
            label = stringResource(R.string.schedule_hourly_at, clockText(hour, 0)),
            value = draft.hourlyBrightness[hour],
            range = 1..255,
            onValueChange = { setHour(hour, it) },
            valueLabel = { SliderValue(brightnessPercent(it)) }
        )
    }
}

/**
 * 24 bars, one per hour: tap a bar to edit that hour with the slider below, or drag across the
 * bars to draw the brightness of several hours at once (the finger's height is the value).
 */
@Composable
private fun HourBars(values: List<Int>, selected: Int, onSelect: (Int) -> Unit, onPaint: (hour: Int, value: Int) -> Unit) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.tertiary
    val currentOnPaint by rememberUpdatedState(onPaint)
    val currentOnSelect by rememberUpdatedState(onSelect)
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnSelect(hourAt(offset.x, size.width)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val value = ((1f - change.position.y / size.height) * 255).roundToInt().coerceIn(1, 255)
                        currentOnPaint(hourAt(change.position.x, size.width), value)
                        change.consume()
                    }
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
        // Hour labels under the centers of their bars
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(24) { h ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                    if (h % 6 == 0) {
                        Text(
                            "$h",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

private fun hourAt(x: Float, width: Int): Int = (x / width * 24).toInt().coerceIn(0, 23)

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
