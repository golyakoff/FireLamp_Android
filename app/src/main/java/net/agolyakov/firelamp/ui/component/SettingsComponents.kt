package net.agolyakov.firelamp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A titled card holding one group of settings. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** A label above a row of mutually exclusive options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(optionLabel(option), maxLines = 1) }
                )
            }
        }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** One option of a group, with an optional hint under the label. */
@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    hint: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Digits of equal width: a changing number (progress, a slider value) does not shift the text around it. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Width of the value shown after a slider: the same everywhere, so sliders line up. */
val SliderValueWidth = 64.dp

/**
 * The value after a slider: fixed width, right-aligned, tabular digits - the slider keeps its
 * length while dragged ("9" -> "120").
 */
@Composable
fun SliderValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.tabular(),
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(SliderValueWidth)
    )
}

/** A label above a [SyncedSlider]; the value is shown after the slider. */
@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    valueLabel: @Composable (Int) -> Unit = { SliderValue(it.toString()) },
    trackBrush: Brush? = null,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        SyncedSlider(
            value = value,
            range = range,
            onValueChange = onValueChange,
            enabled = enabled,
            valueLabel = valueLabel,
            trackBrush = trackBrush
        )
    }
}

/** Brightness `1..255` as people read it: 1..100 %. */
fun brightnessPercent(value: Int): String = "${(value * 100 + 254) / 255}%"

/**
 * A whole-number field. Keeps the typed text while it is not a valid number (empty, out of
 * [range]) and reports only valid values; an invalid entry is shown as an error with [hint].
 */
@Composable
fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    suffix: String? = null,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            if (new.length <= 6 && new.all { it.isDigit() }) {
                text = new
                onValueChange(new.toIntOrNull()?.takeIf { it in range })
            }
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = parsed == null,
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** "Save" for a section edited as a draft: enabled when the draft differs and is valid. */
@Composable
fun SaveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(text)
    }
}
