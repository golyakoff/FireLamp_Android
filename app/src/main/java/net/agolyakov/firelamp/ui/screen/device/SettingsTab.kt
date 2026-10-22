package net.agolyakov.firelamp.ui.screen.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiFind
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.FireLampCodec
import net.agolyakov.firelamp.protocol.LampTime
import net.agolyakov.firelamp.protocol.TimeSource
import net.agolyakov.firelamp.protocol.TimeZoneMapper
import net.agolyakov.firelamp.protocol.WifiMode
import net.agolyakov.firelamp.protocol.WifiScan
import net.agolyakov.firelamp.protocol.WifiScanState
import net.agolyakov.firelamp.protocol.WifiSettings
import net.agolyakov.firelamp.protocol.WifiSettingsUpdate
import net.agolyakov.firelamp.protocol.WifiState
import net.agolyakov.firelamp.protocol.WifiStatus
import net.agolyakov.firelamp.protocol.utf8Length
import net.agolyakov.firelamp.service.wifi.PhoneWifi
import net.agolyakov.firelamp.ui.component.ChoiceRow
import net.agolyakov.firelamp.ui.component.NumberField
import net.agolyakov.firelamp.ui.component.SaveButton
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.theme.FireLampTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The lamp's connection to the world: WiFi, its Bluetooth name, time. */
data class SettingsTabState(
    val wifiSettings: WifiSettings?,
    val wifiStatus: WifiStatus?,
    val wifiScan: WifiScan?,
    val wifiScanSupported: Boolean,
    val deviceName: String?,
    val lampTime: LampTime?,
    val timeZone: String?,
    val phoneTimeZone: String,
)

interface SettingsTabActions {
    fun saveWifiSettings(update: WifiSettingsUpdate)
    fun startWifiScan()
    fun saveDeviceName(name: String)
    fun syncTimeFromPhone()
}

@Composable
fun SettingsTab(state: SettingsTabState, actions: SettingsTabActions) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.wifiSettings?.let {
            item {
                WifiSection(it, state.wifiStatus, state.wifiScan, state.wifiScanSupported, actions::startWifiScan, actions::saveWifiSettings)
            }
        }
        state.deviceName?.let { item { NameSection(it, actions::saveDeviceName) } }
        item { TimeSection(state.lampTime, state.timeZone, state.phoneTimeZone, actions::syncTimeFromPhone) }
    }
}

// ---------- WiFi ----------

@Composable
private fun WifiSection(
    saved: WifiSettings,
    status: WifiStatus?,
    scan: WifiScan?,
    scanSupported: Boolean,
    onScan: () -> Unit,
    onSave: (WifiSettingsUpdate) -> Unit,
) {
    var mode by rememberSaveable(saved) { mutableStateOf(saved.mode) }
    // The lamp checks the period even in the off mode; a stored 0 would be rejected back
    var period by rememberSaveable(saved) { mutableStateOf(saved.periodHours.coerceIn(1, 24)) }
    var periodValid by remember(saved) { mutableStateOf(true) }
    var ssid by rememberSaveable(saved) { mutableStateOf(saved.ssid) }
    // Not saveable: a password does not belong in the saved instance state
    var password by remember(saved) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    // Picked from the lamp's scan: an open network needs no password (and clears a stored one)
    var pickedOpen by rememberSaveable(saved) { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var phoneNote by remember { mutableStateOf<String?>(null) }

    val ssidValid = ssid.utf8Length() <= WifiSettings.MAX_SSID_BYTES && (mode == WifiMode.OFF || ssid.isNotEmpty())
    val passwordValid = password.utf8Length() <= WifiSettings.MAX_PASSWORD_BYTES
    val changed = mode != saved.mode || period != saved.periodHours || ssid != saved.ssid || password.isNotEmpty() ||
        (pickedOpen && saved.passwordStored)

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
            onValueChange = {
                ssid = it
                pickedOpen = false
            },
            label = { Text(stringResource(R.string.settings_wifi_ssid)) },
            supportingText = { Text(stringResource(R.string.wifi_band_hint)) },
            isError = !ssidValid,
            singleLine = true,
            trailingIcon = if (scanSupported) {
                {
                    IconButton(onClick = {
                        showPicker = true
                        if (scan?.state != WifiScanState.SCANNING) onScan()
                    }) {
                        Icon(Icons.Filled.WifiFind, contentDescription = stringResource(R.string.wifi_picker_title))
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        if (!scanSupported) {
            PhoneNetworkButton(
                onNetwork = { network, note ->
                    ssid = network
                    pickedOpen = false
                    phoneNote = note
                },
                onFailure = { phoneNote = it }
            )
        }
        phoneNote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!pickedOpen) {
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
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.password_hide else R.string.password_show)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        SaveButton(
            text = stringResource(R.string.save),
            enabled = changed && ssidValid && passwordValid && (mode != WifiMode.PERIODIC || periodValid),
            onClick = {
                // An empty field keeps the stored password (it is never read back); an open
                // network picked from the scan clears it
                val newPassword = if (pickedOpen) "" else password.ifEmpty { null }
                onSave(WifiSettingsUpdate(mode, period, ssid, newPassword))
            }
        )
    }

    if (showPicker) {
        WifiNetworkPicker(
            scan = scan,
            onRescan = onScan,
            onPick = { network ->
                ssid = network.ssid
                pickedOpen = !network.needsPassword
                password = ""
                phoneNote = null
                if (mode == WifiMode.OFF) mode = WifiMode.PERIODIC
                showPicker = false
            },
            onManual = { showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Firmware without the WiFi scan: take the network the phone is on. Android tells the SSID only
 * with the location permission, asked here and only for this.
 */
@Composable
private fun PhoneNetworkButton(onNetwork: (String, note: String?) -> Unit, onFailure: (String) -> Unit) {
    val context = LocalContext.current
    val notFound = stringResource(R.string.wifi_phone_not_found)
    val is5GHz = stringResource(R.string.wifi_phone_5ghz)
    fun take() {
        val network = PhoneWifi.current(context)
        when {
            network == null -> onFailure(notFound)
            network.is24GHz -> onNetwork(network.ssid, null)
            // Often the same router serves 2.4 GHz under the same name: fill it in, but warn
            else -> onNetwork(network.ssid, is5GHz.format(network.ssid))
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (PhoneWifi.hasPermission(context)) take() else onFailure(notFound)
    }
    TextButton(onClick = {
        if (PhoneWifi.hasPermission(context)) take() else launcher.launch(PhoneWifi.PERMISSIONS)
    }) {
        Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
        Text(stringResource(R.string.wifi_phone_network), modifier = Modifier.padding(start = 8.dp))
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
    var name by rememberSaveable(saved) { mutableStateOf(saved) }
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
            stringResource(R.string.settings_time_zone, timeZone?.let { zoneText(it) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium
        )
        if (timeZone != null && timeZone != phoneTimeZone) {
            Text(
                stringResource(R.string.settings_time_zone_phone, zoneText(phoneTimeZone)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_time_sync))
        }
    }
}

/** The lamp's POSIX zone as people read it: "UTC+1, UTC+2 in summer" (the raw string if unknown). */
@Composable
private fun zoneText(posix: String): String {
    val offsets = TimeZoneMapper.parseOffsets(posix) ?: return posix
    val standard = TimeZoneMapper.formatUtc(offsets.standardMinutes)
    return offsets.daylightMinutes?.let { stringResource(R.string.settings_time_zone_dst, standard, TimeZoneMapper.formatUtc(it)) }
        ?: standard
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun SettingsTabPreview() {
    FireLampTheme {
        SettingsTab(
            state = SettingsTabState(
                wifiSettings = WifiSettings(WifiMode.PERIODIC, 12, "home", passwordStored = true),
                wifiStatus = WifiStatus(WifiState.OFF, null, 0),
                wifiScan = null,
                wifiScanSupported = true,
                deviceName = "",
                lampTime = LampTime(1_793_000_000, valid = true, source = TimeSource.BLE),
                timeZone = "MSK-3",
                phoneTimeZone = "CET-1CEST,M3.5.0,M10.5.0/3",
            ),
            actions = object : SettingsTabActions {
                override fun saveWifiSettings(update: WifiSettingsUpdate) {}
                override fun startWifiScan() {}
                override fun saveDeviceName(name: String) {}
                override fun syncTimeFromPhone() {}
            }
        )
    }
}
