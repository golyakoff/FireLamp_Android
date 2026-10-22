package net.agolyakov.firelamp.ui.screen.device

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import net.agolyakov.firelamp.ui.component.RadioRow
import net.agolyakov.firelamp.ui.component.SaveButton
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.component.SwitchRow
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The lamp as a device: firmware update, matrix (LED settings), controls (buttons / touch pad),
 * what the lamp is, reboot and resets. Matrix and controls take effect after a reboot: the
 * screen shows a banner with "Reboot now" once saved.
 */
@Composable
fun SystemTab(
    deviceInfo: DeviceInfo?,
    ledSettings: LedSettings?,
    hardwareSettings: HardwareSettings?,
    onSaveLedSettings: (LedSettings) -> Unit,
    onSaveHardwareSettings: (Controls, buttonsActiveHigh: Boolean) -> Unit,
    onReboot: () -> Unit,
    onResetEffects: () -> Unit,
    onFactoryReset: () -> Unit,
    firmware: @Composable () -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { firmware() }
        ledSettings?.let { item { MatrixSection(it, onSaveLedSettings) } }
        hardwareSettings?.let { item { ControlsSection(it, onSaveHardwareSettings) } }
        deviceInfo?.let { item { LampInfoSection(it) } }
        item {
            OutlinedButton(onClick = onReboot, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hw_reboot))
            }
        }
        item { ResetSection(onResetEffects, onFactoryReset) }
    }
}

// ---------- Resets ----------

@Composable
private fun ResetSection(onResetEffects: () -> Unit, onFactoryReset: () -> Unit) {
    var confirm by remember { mutableStateOf<Int?>(null) }
    SettingsSection(stringResource(R.string.settings_reset)) {
        OutlinedButton(onClick = { confirm = R.string.settings_reset_effects_confirm }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_reset_effects))
        }
        OutlinedButton(onClick = { confirm = R.string.settings_factory_reset_confirm }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_factory_reset), color = MaterialTheme.colorScheme.error)
        }
    }
    confirm?.let { message ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (message == R.string.settings_factory_reset_confirm) onFactoryReset() else onResetEffects()
                    confirm = null
                }) { Text(stringResource(R.string.settings_reset_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun MatrixSection(saved: LedSettings, onSave: (LedSettings) -> Unit) {
    // Edited as a draft and written as a whole: the lamp checks the fields together
    var draft by rememberSaveable(saved) { mutableStateOf(saved) }
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
        MatrixDiagram(draft)
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

/**
 * The strip's path over the upright picture for the chosen wiring, rotation and mirror, on a
 * small model of the matrix (same shape: wide, tall or square). The big dot is the first LED.
 */
@Composable
private fun MatrixDiagram(settings: LedSettings) {
    val modelWidth = if (settings.width > settings.height) 5 else 4
    val modelHeight = if (settings.height > settings.width) 5 else 4
    val path = remember(modelWidth, modelHeight, settings.rotation, settings.mirror, settings.wiring) {
        MatrixLayout.stripPath(modelWidth, modelHeight, settings.rotation, settings.mirror, settings.wiring)
    }
    val (pictureWidth, pictureHeight) = MatrixLayout.pictureSize(modelWidth, modelHeight, settings.rotation)
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.outline
    val startColor = MaterialTheme.colorScheme.tertiary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .width(DIAGRAM_CELL * pictureWidth)
                .height(DIAGRAM_CELL * pictureHeight)
        ) {
            val cell = size.width / pictureWidth
            // Picture y = 0 is the bottom row
            fun center(c: MatrixLayout.Cell) = Offset((c.x + 0.5f) * cell, (pictureHeight - 1 - c.y + 0.5f) * cell)

            path.forEach { drawCircle(dotColor, cell * 0.1f, center(it)) }
            val line = Path().apply {
                moveTo(center(path.first()).x, center(path.first()).y)
                path.drop(1).forEach { lineTo(center(it).x, center(it).y) }
            }
            val stroke = 3.dp.toPx()
            drawPath(line, lineColor, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Arrowhead at the end of the strip
            val end = center(path.last())
            val before = center(path[path.size - 2])
            val angle = atan2(end.y - before.y, end.x - before.x)
            val head = cell * 0.3f
            for (side in listOf(-1, 1)) {
                val a = angle + PI.toFloat() + side * 0.5f
                drawLine(lineColor, end, end + Offset(cos(a), sin(a)) * head, stroke, StrokeCap.Round)
            }
            drawCircle(startColor, cell * 0.22f, center(path.first()))
        }
    }
}

private val DIAGRAM_CELL = 32.dp

@Composable
private fun ControlsSection(
    saved: HardwareSettings,
    onSave: (Controls, Boolean) -> Unit,
) {
    var controls by rememberSaveable(saved) { mutableStateOf(saved.controls) }
    var activeHigh by rememberSaveable(saved) { mutableStateOf(saved.buttonsActiveHigh) }

    SettingsSection(stringResource(R.string.hw_controls)) {
        Column {
            saved.supportedControls.forEach { option ->
                RadioRow(
                    label = stringResource(controlsLabel(option)),
                    hint = stringResource(controlsHint(option)),
                    selected = option == controls,
                    onClick = { controls = option }
                )
            }
        }
        // Main/Up/Down: a touch pad has no buttons, the Reset button is always active low
        if (controls != Controls.TOUCH_PAD) {
            Text(stringResource(R.string.hw_buttons_wiring), style = MaterialTheme.typography.bodyMedium)
            Column {
                RadioRow(
                    label = stringResource(R.string.hw_buttons_to_gnd),
                    selected = !activeHigh,
                    onClick = { activeHigh = false }
                )
                RadioRow(
                    label = stringResource(R.string.hw_buttons_to_vcc),
                    selected = activeHigh,
                    onClick = { activeHigh = true }
                )
            }
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

/** What the lamp is, with a button that copies it all as text (for a bug report). */
@Composable
private fun LampInfoSection(info: DeviceInfo) {
    val yes = stringResource(R.string.hw_info_found)
    val no = stringResource(R.string.hw_info_not_found)
    val rows = listOf(
        stringResource(R.string.hw_info_firmware) to info.firmwareVersion,
        stringResource(R.string.hw_info_build_date) to info.buildDate,
        stringResource(R.string.hw_info_variant) to info.buildVariant,
        stringResource(R.string.hw_info_chip) to when (info.chip) {
            Chip.ESP32 -> "ESP32"
            Chip.ESP32_C3 -> "ESP32-C3"
            Chip.UNKNOWN -> stringResource(R.string.unknown)
        },
        stringResource(R.string.hw_info_flash) to stringResource(R.string.unit_mb, info.flashSizeMb),
        stringResource(R.string.hw_info_matrix) to "${info.matrixWidth} × ${info.matrixHeight}",
        stringResource(R.string.hw_info_effects) to info.effectCount.toString(),
        stringResource(R.string.hw_info_rtc) to if (info.hasRtc) yes else no,
        stringResource(R.string.hw_info_storage) to
            stringResource(if (info.hasEeprom) R.string.hw_info_storage_eeprom else R.string.hw_info_storage_flash),
        stringResource(R.string.hw_info_protocol) to info.protocolVersion.toString(),
    )
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SettingsSection(stringResource(R.string.hw_lamp_info)) {
        rows.forEach { (label, value) -> InfoRow(label, value) }
        TextButton(onClick = {
            val text = rows.joinToString("\n") { (label, value) -> "$label: $value" }
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("FireLamp", text))) }
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.hw_info_copy), modifier = Modifier.padding(start = 8.dp))
        }
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
private fun SystemTabPreview() {
    FireLampTheme {
        SystemTab(
            deviceInfo = DeviceInfo(1, Chip.ESP32_C3, 4, 0b1101, 16, 16, 32, Controls.ONE_BUTTON, "0.1.1", "Oct 21 2026 22:42:08", "default"),
            ledSettings = LedSettings(16, 16, LedType.WS2812, ColorOrder.GRB, Rotation.NONE, false, 2000, Wiring.SERPENTINE),
            hardwareSettings = HardwareSettings(Controls.ONE_BUTTON, 0b011, false),
            onSaveLedSettings = {},
            onSaveHardwareSettings = { _, _ -> },
            onReboot = {},
            onResetEffects = {},
            onFactoryReset = {},
        )
    }
}
