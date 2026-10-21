package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.DemoOrder
import net.agolyakov.firelamp.protocol.DemoSettings
import net.agolyakov.firelamp.protocol.EffectInfo
import net.agolyakov.firelamp.protocol.FireLampCodec
import net.agolyakov.firelamp.protocol.LampState
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.TimeSource
import net.agolyakov.firelamp.protocol.WifiMode
import net.agolyakov.firelamp.protocol.WifiSettings
import net.agolyakov.firelamp.protocol.WifiSettingsUpdate
import net.agolyakov.firelamp.protocol.WifiState
import net.agolyakov.firelamp.protocol.WifiStatus
import net.agolyakov.firelamp.protocol.utf8Length
import net.agolyakov.firelamp.ui.component.ChoiceRow
import net.agolyakov.firelamp.ui.component.NumberField
import net.agolyakov.firelamp.ui.component.SaveButton
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.component.SyncedSlider
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the lamp keeps that is not hardware: demo mode, WiFi, name, time, resets. */
data class SettingsTabState(
    val lampState: LampState?,
    val effects: List<EffectInfo>,
    val demoSettings: DemoSettings?,
    val wifiSettings: WifiSettings?,
    val wifiStatus: WifiStatus?,
    val deviceName: String?,
    val lampTime: LampTime?,
    val timeZone: String?,
    val phoneTimeZone: String,
)

interface SettingsTabActions {
    fun saveDemoSettings(settings: DemoSettings)
    fun setDemoIncluded(effectCode: String, include: Boolean)
    fun saveWifiSettings(update: WifiSettingsUpdate)
    fun saveDeviceName(name: String)
    fun syncTimeFromPhone()
    fun setFps(fps: Int)
    fun resetEffects()
    fun factoryReset()
}

@Composable
fun SettingsTab(state: SettingsTabState, actions: SettingsTabActions) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.demoSettings?.let { item { DemoSection(it, state.effects, actions) } }
        state.wifiSettings?.let { item { WifiSection(it, state.wifiStatus, actions::saveWifiSettings) } }
        state.deviceName?.let { item { NameSection(it, actions::saveDeviceName) } }
        item { TimeSection(state.lampTime, state.timeZone, state.phoneTimeZone, actions::syncTimeFromPhone) }
        state.lampState?.let { item { FpsSection(it.fps, actions::setFps) } }
        item { ResetSection(actions) }
    }
}

// ---------- Demo mode ----------

@Composable
private fun DemoSection(saved: DemoSettings, effects: List<EffectInfo>, actions: SettingsTabActions) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var durationValid by remember(saved) { mutableStateOf(true) }
    var additionValid by remember(saved) { mutableStateOf(true) }

    SettingsSection(stringResource(R.string.settings_demo)) {
        ChoiceRow(
            label = stringResource(R.string.settings_demo_order),
            options = DemoOrder.entries,
            selected = draft.order,
            optionLabel = {
                stringResource(if (it == DemoOrder.SEQUENTIAL) R.string.settings_demo_sequential else R.string.settings_demo_random)
            },
            onSelect = { draft = draft.copy(order = it) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = stringResource(R.string.settings_demo_duration),
                value = draft.durationSeconds,
                range = DemoSettings.DURATION_RANGE,
                onValueChange = { v -> durationValid = v != null; v?.let { draft = draft.copy(durationSeconds = it) } },
                suffix = stringResource(R.string.unit_seconds),
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = stringResource(R.string.settings_demo_random_addition),
                value = draft.randomAdditionSeconds,
                range = DemoSettings.RANDOM_ADDITION_RANGE,
                onValueChange = { v -> additionValid = v != null; v?.let { draft = draft.copy(randomAdditionSeconds = it) } },
                suffix = stringResource(R.string.unit_seconds),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            stringResource(R.string.settings_demo_duration_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        SaveButton(
            text = stringResource(R.string.save),
            enabled = durationValid && additionValid && draft != saved,
            onClick = { actions.saveDemoSettings(draft) }
        )

        Text(stringResource(R.string.settings_demo_effects), style = MaterialTheme.typography.bodyMedium)
        val included = effects.count { it.inDemo }
        Column {
            effects.forEach { effect ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = effect.inDemo,
                        // The lamp rejects excluding the last included effect
                        enabled = !(effect.inDemo && included == 1),
                        onCheckedChange = { actions.setDemoIncluded(effect.code, it) }
                    )
                    Text(effectName(effect), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ---------- WiFi ----------

@Composable
private fun WifiSection(saved: WifiSettings, status: WifiStatus?, onSave: (WifiSettingsUpdate) -> Unit) {
    var mode by remember(saved) { mutableStateOf(saved.mode) }
    // The lamp checks the period even in the off mode; a stored 0 would be rejected back
    var period by remember(saved) { mutableStateOf(saved.periodHours.coerceIn(1, 24)) }
    var periodValid by remember(saved) { mutableStateOf(true) }
    var ssid by remember(saved) { mutableStateOf(saved.ssid) }
    var password by remember(saved) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val ssidValid = ssid.utf8Length() <= WifiSettings.MAX_SSID_BYTES && (mode == WifiMode.OFF || ssid.isNotEmpty())
    val passwordValid = password.utf8Length() <= WifiSettings.MAX_PASSWORD_BYTES
    val changed = mode != saved.mode || period != saved.periodHours || ssid != saved.ssid || password.isNotEmpty()

    SettingsSection(stringResource(R.string.settings_wifi)) {
        status?.let { WifiStatusLine(it) }
        ChoiceRow(
            label = stringResource(R.string.settings_wifi_mode),
            options = WifiMode.entries,
            selected = mode,
            optionLabel = {
                stringResource(
                    when (it) {
                        WifiMode.OFF -> R.string.settings_wifi_off
                        WifiMode.PERIODIC -> R.string.settings_wifi_periodic
                        WifiMode.ALWAYS_ON -> R.string.settings_wifi_always
                    }
                )
            },
            onSelect = { mode = it }
        )
        Text(
            stringResource(
                when (mode) {
                    WifiMode.OFF -> R.string.settings_wifi_off_hint
                    WifiMode.PERIODIC -> R.string.settings_wifi_periodic_hint
                    WifiMode.ALWAYS_ON -> R.string.settings_wifi_always_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        if (mode == WifiMode.PERIODIC) {
            NumberField(
                label = stringResource(R.string.settings_wifi_period),
                value = period,
                range = 1..24,
                onValueChange = { v -> periodValid = v != null; v?.let { period = it } },
                suffix = stringResource(R.string.unit_hours),
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(R.string.settings_wifi_ssid)) },
            isError = !ssidValid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.settings_wifi_password)) },
            placeholder = {
                Text(stringResource(if (saved.passwordStored) R.string.settings_wifi_password_kept else R.string.settings_wifi_password_none))
            },
            isError = !passwordValid,
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        SaveButton(
            text = stringResource(R.string.save),
            enabled = changed && ssidValid && passwordValid && (mode != WifiMode.PERIODIC || periodValid),
            onClick = {
                // An empty field keeps the stored password (it is never read back)
                onSave(WifiSettingsUpdate(mode, period, ssid, password.ifEmpty { null }))
            }
        )
    }
}

@Composable
private fun WifiStatusLine(status: WifiStatus) {
    val text = when (status.state) {
        WifiState.OFF -> stringResource(R.string.settings_wifi_state_off)
        WifiState.CONNECTING -> stringResource(R.string.settings_wifi_state_connecting)
        WifiState.CONNECTED -> stringResource(R.string.settings_wifi_state_connected, status.ip ?: "", status.rssi)
        WifiState.ERROR -> stringResource(R.string.settings_wifi_state_error)
        WifiState.UNKNOWN -> stringResource(R.string.unknown)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = when (status.state) {
            WifiState.CONNECTED -> MaterialTheme.colorScheme.primary
            WifiState.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
    )
}

// ---------- Name ----------

@Composable
private fun NameSection(saved: String, onSave: (String) -> Unit) {
    var name by remember(saved) { mutableStateOf(saved) }
    val valid = name.utf8Length() <= FireLampCodec.DEVICE_NAME_MAX_BYTES && name.none { it < ' ' }
    SettingsSection(stringResource(R.string.settings_name)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.settings_name_label)) },
            supportingText = { Text(stringResource(R.string.settings_name_hint, FireLampCodec.DEVICE_NAME_MAX_BYTES)) },
            isError = !valid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SaveButton(
            text = stringResource(R.string.save),
            enabled = valid && name != saved,
            onClick = { onSave(name.trim()) }
        )
    }
}

// ---------- Time ----------

@Composable
private fun TimeSection(lampTime: LampTime?, timeZone: String?, phoneTimeZone: String, onSync: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_time)) {
        val time = lampTime?.takeIf { it.valid }?.let {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .format(Instant.ofEpochSecond(it.epochSeconds).atZone(ZoneId.systemDefault()))
        } ?: stringResource(R.string.settings_time_not_set)
        val source = lampTime?.let {
            stringResource(
                when (it.source) {
                    TimeSource.RTC -> R.string.settings_time_source_rtc
                    TimeSource.BLE -> R.string.settings_time_source_ble
                    TimeSource.SNTP -> R.string.settings_time_source_sntp
                    TimeSource.HTTP_API -> R.string.settings_time_source_api
                    TimeSource.NONE, TimeSource.UNKNOWN -> R.string.settings_time_source_none
                }
            )
        }
        Text(listOfNotNull(time, source).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_time_zone, timeZone ?: "—"),
            style = MaterialTheme.typography.bodyMedium
        )
        if (timeZone != null && timeZone != phoneTimeZone) {
            Text(
                stringResource(R.string.settings_time_zone_phone, phoneTimeZone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_time_sync))
        }
    }
}

// ---------- Frame rate ----------

@Composable
private fun FpsSection(fps: Int, onChange: (Int) -> Unit) {
    SettingsSection(stringResource(R.string.settings_fps)) {
        SyncedSlider(
            value = fps,
            range = 1..120,
            onValueChange = onChange,
            valueLabel = { Text(stringResource(R.string.unit_fps, it), modifier = Modifier.padding(start = 12.dp)) }
        )
        Text(
            stringResource(R.string.settings_fps_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ---------- Resets ----------

@Composable
private fun ResetSection(actions: SettingsTabActions) {
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
                    if (message == R.string.settings_factory_reset_confirm) actions.factoryReset() else actions.resetEffects()
                    confirm = null
                }) { Text(stringResource(R.string.settings_reset_do)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@Preview(showBackground = true, heightDp = 2200)
@Composable
private fun SettingsTabPreview() {
    FireLampTheme {
        SettingsTab(
            state = SettingsTabState(
                lampState = LampState(true, 128, 60, false, "fire"),
                effects = listOf(
                    EffectInfo(0, 2, "fire", "Fire", true, emptyList()),
                    EffectInfo(1, 2, "rain", "Rain", false, emptyList()),
                ),
                demoSettings = DemoSettings(DemoOrder.RANDOM, 300, 60),
                wifiSettings = WifiSettings(WifiMode.PERIODIC, 12, "home", passwordStored = true),
                wifiStatus = WifiStatus(WifiState.OFF, null, 0),
                deviceName = "",
                lampTime = LampTime(1_793_000_000, valid = true, source = TimeSource.BLE),
                timeZone = "MSK-3",
                phoneTimeZone = "CET-1CEST,M3.5.0,M10.5.0/3",
            ),
            actions = object : SettingsTabActions {
                override fun saveDemoSettings(settings: DemoSettings) {}
                override fun setDemoIncluded(effectCode: String, include: Boolean) {}
                override fun saveWifiSettings(update: WifiSettingsUpdate) {}
                override fun saveDeviceName(name: String) {}
                override fun syncTimeFromPhone() {}
                override fun setFps(fps: Int) {}
                override fun resetEffects() {}
                override fun factoryReset() {}
            }
        )
    }
}
