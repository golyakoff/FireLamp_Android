package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.DemoOrder
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.ui.component.ChoiceRow
import net.agolyakov.firelamp.ui.component.NumberField
import net.agolyakov.firelamp.ui.component.SaveButton

/**
 * Demo mode settings, opened from the demo switch on the Lamp tab: the timing (a draft with
 * Save, one characteristic) and which effects take part (each checkbox applies at once).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoSettingsSheet(
    saved: DemoSettings?,
    effects: List<EffectInfo>,
    onSave: (DemoSettings) -> Unit,
    onSetIncluded: (effectCode: String, include: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            item { Text(stringResource(R.string.settings_demo), style = MaterialTheme.typography.titleLarge) }
            saved?.let { item { DemoTiming(it, onSave) } }
            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.settings_demo_effects),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    stringResource(R.string.settings_demo_effects_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            val included = effects.count { it.inDemo }
            items(effects, key = { it.code }) { effect ->
                // The lamp rejects excluding the last included effect
                val enabled = !(effect.inDemo && included == 1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = effect.inDemo,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onSetIncluded(effect.code, it) }
                        )
                ) {
                    Checkbox(checked = effect.inDemo, enabled = enabled, onCheckedChange = null)
                    Text(effectName(effect), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun DemoTiming(saved: DemoSettings, onSave: (DemoSettings) -> Unit) {
    var draft by rememberSaveable(saved) { mutableStateOf(saved) }
    var durationValid by remember(saved) { mutableStateOf(true) }
    var additionValid by remember(saved) { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceRow(
            label = stringResource(R.string.settings_demo_order),
            options = DemoOrder.entries,
            selected = draft.order,
            optionLabel = {
                stringResource(if (it == DemoOrder.SEQUENTIAL) R.string.settings_demo_sequential else R.string.settings_demo_random)
            },
            onSelect = { draft = draft.copy(order = it) }
        )
        NumberField(
            label = stringResource(R.string.settings_demo_duration),
            value = draft.durationSeconds,
            range = DemoSettings.DURATION_RANGE,
            onValueChange = { v -> durationValid = v != null; v?.let { draft = draft.copy(durationSeconds = it) } },
            suffix = stringResource(R.string.unit_seconds),
            modifier = Modifier.fillMaxWidth()
        )
        NumberField(
            label = stringResource(R.string.settings_demo_random_addition),
            value = draft.randomAdditionSeconds,
            range = DemoSettings.RANDOM_ADDITION_RANGE,
            onValueChange = { v -> additionValid = v != null; v?.let { draft = draft.copy(randomAdditionSeconds = it) } },
            suffix = stringResource(R.string.unit_seconds),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.settings_demo_duration_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        SaveButton(
            text = stringResource(R.string.save),
            enabled = durationValid && additionValid && draft != saved,
            onClick = { onSave(draft) }
        )
    }
}
