package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.Chip
import net.agolyakov.firelamp.protocol.ColorOrder
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.protocol.DeviceInfo
import net.agolyakov.firelamp.protocol.HardwareSettings
import net.agolyakov.firelamp.protocol.LedSettings
import net.agolyakov.firelamp.protocol.LedType
import net.agolyakov.firelamp.protocol.Rotation
import net.agolyakov.firelamp.protocol.Wiring
import net.agolyakov.firelamp.ui.component.ChoiceRow
import net.agolyakov.firelamp.ui.component.NumberField
import net.agolyakov.firelamp.ui.component.SaveButton
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.component.SwitchRow
import net.agolyakov.firelamp.ui.theme.FireLampTheme

/**
 * Matrix (LED settings), controls (buttons / touch pad) and what the lamp is. Everything here
 * takes effect after a reboot: the screen shows a banner with "Reboot now" once saved.
 */
@Composable
fun HardwareTab(
    deviceInfo: DeviceInfo?,
    ledSettings: LedSettings?,
    hardwareSettings: HardwareSettings?,
    onSaveLedSettings: (LedSettings) -> Unit,
    onSaveHardwareSettings: (Controls, buttonsActiveHigh: Boolean) -> Unit,
    onReboot: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ledSettings?.let { item { MatrixSection(it, onSaveLedSettings) } }
        hardwareSettings?.let { item { ControlsSection(it, onSaveHardwareSettings) } }
        deviceInfo?.let { item { LampInfoSection(it) } }
        item {
            OutlinedButton(onClick = onReboot, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hw_reboot))
            }
        }
    }
}

@Composable
private fun MatrixSection(saved: LedSettings, onSave: (LedSettings) -> Unit) {
    // Edited as a draft and written as a whole: the lamp checks the fields together
    var draft by remember(saved) { mutableStateOf(saved) }
    var widthValid by remember(saved) { mutableStateOf(true) }
    var heightValid by remember(saved) { mutableStateOf(true) }
    var limitValid by remember(saved) { mutableStateOf(true) }

    val leds = draft.width * draft.height
    val sizeValid = widthValid && heightValid && leds <= LedSettings.MAX_LEDS
    val minLimit = LedSettings.minCurrentLimitMa(draft.width, draft.height)
    val valid = sizeValid && limitValid && draft.currentLimitMa >= minLimit

    SettingsSection(stringResource(R.string.hw_matrix)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = stringResource(R.string.hw_width),
                value = draft.width,
                range = LedSettings.MIN_SIZE..LedSettings.MAX_SIZE,
                onValueChange = { v -> widthValid = v != null; v?.let { draft = draft.copy(width = it) } },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = stringResource(R.string.hw_height),
                value = draft.height,
                range = LedSettings.MIN_SIZE..LedSettings.MAX_SIZE,
                onValueChange = { v -> heightValid = v != null; v?.let { draft = draft.copy(height = it) } },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = if (sizeValid) stringResource(R.string.hw_led_count, leds)
            else stringResource(R.string.hw_size_invalid, LedSettings.MIN_SIZE, LedSettings.MAX_SIZE, LedSettings.MAX_LEDS),
            style = MaterialTheme.typography.bodySmall,
            color = if (sizeValid) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
        )

        ChoiceRow(
            label = stringResource(R.string.hw_led_type),
            options = LedType.entries,
            selected = draft.ledType,
            optionLabel = { it.name },
            onSelect = { draft = draft.copy(ledType = it) }
        )
        ChoiceRow(
            label = stringResource(R.string.hw_color_order),
            options = ColorOrder.entries,
            selected = draft.colorOrder,
            optionLabel = { it.name },
            onSelect = { draft = draft.copy(colorOrder = it) }
        )
        ChoiceRow(
            label = stringResource(R.string.hw_wiring),
            options = Wiring.entries,
            selected = draft.wiring,
            optionLabel = {
                stringResource(if (it == Wiring.SERPENTINE) R.string.hw_wiring_serpentine else R.string.hw_wiring_progressive)
            },
            onSelect = { draft = draft.copy(wiring = it) }
        )
        ChoiceRow(
            label = stringResource(R.string.hw_rotation),
            options = Rotation.entries,
            selected = draft.rotation,
            optionLabel = { "${it.degrees}°" },
            onSelect = { draft = draft.copy(rotation = it) }
        )
        SwitchRow(
            label = stringResource(R.string.hw_mirror),
            checked = draft.mirror,
            onCheckedChange = { draft = draft.copy(mirror = it) }
        )
        Text(
            stringResource(R.string.hw_orientation_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        NumberField(
            label = stringResource(R.string.hw_current_limit),
            value = draft.currentLimitMa,
            range = 0..LedSettings.MAX_CURRENT_LIMIT_MA,
            onValueChange = { v -> limitValid = v != null; v?.let { draft = draft.copy(currentLimitMa = it) } },
            suffix = stringResource(R.string.unit_ma),
            hint = stringResource(R.string.hw_current_limit_hint, minLimit),
            modifier = Modifier.fillMaxWidth()
        )

        SaveButton(
            text = stringResource(R.string.save),
            enabled = valid && draft != saved,
            onClick = { onSave(draft) }
        )
    }
}

@Composable
private fun ControlsSection(
    saved: HardwareSettings,
    onSave: (Controls, Boolean) -> Unit,
) {
    var controls by remember(saved) { mutableStateOf(saved.controls) }
    var activeHigh by remember(saved) { mutableStateOf(saved.buttonsActiveHigh) }

    SettingsSection(stringResource(R.string.hw_controls)) {
        Column {
            saved.supportedControls.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == controls, role = Role.RadioButton) { controls = option }
                ) {
                    RadioButton(selected = option == controls, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(controlsLabel(option)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(controlsHint(option)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        // Main/Up/Down: a touch pad has no buttons, the Reset button is always active low
        if (controls != Controls.TOUCH_PAD) {
            SwitchRow(
                label = stringResource(R.string.hw_buttons_active_high),
                hint = stringResource(
                    if (activeHigh) R.string.hw_buttons_active_high_hint else R.string.hw_buttons_active_low_hint
                ),
                checked = activeHigh,
                onCheckedChange = { activeHigh = it }
            )
        }
        SaveButton(
            text = stringResource(R.string.save),
            enabled = saved.isSupported(controls) && (controls != saved.controls || activeHigh != saved.buttonsActiveHigh),
            onClick = { onSave(controls, activeHigh) }
        )
    }
}

private fun controlsLabel(controls: Controls) = when (controls) {
    Controls.ONE_BUTTON -> R.string.hw_controls_one_button
    Controls.FOUR_BUTTONS -> R.string.hw_controls_four_buttons
    Controls.TOUCH_PAD -> R.string.hw_controls_touch_pad
    Controls.UNKNOWN -> R.string.unknown
}

private fun controlsHint(controls: Controls) = when (controls) {
    Controls.ONE_BUTTON -> R.string.hw_controls_one_button_hint
    Controls.FOUR_BUTTONS -> R.string.hw_controls_four_buttons_hint
    Controls.TOUCH_PAD -> R.string.hw_controls_touch_pad_hint
    Controls.UNKNOWN -> R.string.unknown
}

@Composable
private fun LampInfoSection(info: DeviceInfo) {
    val yes = stringResource(R.string.hw_info_found)
    val no = stringResource(R.string.hw_info_not_found)
    SettingsSection(stringResource(R.string.hw_lamp_info)) {
        InfoRow(stringResource(R.string.hw_info_firmware), info.firmwareVersion)
        InfoRow(stringResource(R.string.hw_info_build_date), info.buildDate)
        InfoRow(stringResource(R.string.hw_info_variant), info.buildVariant)
        InfoRow(
            stringResource(R.string.hw_info_chip),
            when (info.chip) {
                Chip.ESP32 -> "ESP32"
                Chip.ESP32_C3 -> "ESP32-C3"
                Chip.UNKNOWN -> stringResource(R.string.unknown)
            }
        )
        InfoRow(stringResource(R.string.hw_info_flash), stringResource(R.string.unit_mb, info.flashSizeMb))
        InfoRow(stringResource(R.string.hw_info_matrix), "${info.matrixWidth} × ${info.matrixHeight}")
        InfoRow(stringResource(R.string.hw_info_effects), info.effectCount.toString())
        InfoRow(stringResource(R.string.hw_info_rtc), if (info.hasRtc) yes else no)
        InfoRow(
            stringResource(R.string.hw_info_storage),
            stringResource(if (info.hasEeprom) R.string.hw_info_storage_eeprom else R.string.hw_info_storage_flash)
        )
        InfoRow(stringResource(R.string.hw_info_protocol), info.protocolVersion.toString())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun HardwareTabPreview() {
    FireLampTheme {
        HardwareTab(
            deviceInfo = DeviceInfo(1, Chip.ESP32_C3, 4, 0b1101, 16, 16, 32, Controls.ONE_BUTTON, "0.1.1", "Oct 21 2026 22:42:08", "default"),
            ledSettings = LedSettings(16, 16, LedType.WS2812, ColorOrder.GRB, Rotation.NONE, false, 2000, Wiring.SERPENTINE),
            hardwareSettings = HardwareSettings(Controls.ONE_BUTTON, 0b011, false),
            onSaveLedSettings = {},
            onSaveHardwareSettings = { _, _ -> },
            onReboot = {},
        )
    }
}
