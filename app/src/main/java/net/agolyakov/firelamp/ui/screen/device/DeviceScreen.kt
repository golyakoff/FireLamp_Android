package net.agolyakov.firelamp.ui.screen.device

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.data.model.ble.ConnectionState
import net.agolyakov.firelamp.data.model.ble.FireLampDevice
import net.agolyakov.firelamp.protocol.WifiMode
import net.agolyakov.firelamp.ui.theme.FireLampTheme

// ----------------------------------------------------------------------------
// Tabs of the device screen, grouped by purpose:
//  - Lamp:     power, brightness, effects and their parameters, demo mode
//  - Schedule: power on/off, dawn, hourly brightness
//  - Settings: demo settings, WiFi, name, time zone, resets
//  - Hardware: matrix (LED settings), controls (buttons/touch), lamp info
// ----------------------------------------------------------------------------
enum class DeviceTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Lamp(R.string.tab_lamp, Icons.Filled.Lightbulb),
    Schedule(R.string.tab_schedule, Icons.Filled.Schedule),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
    Hardware(R.string.tab_hardware, Icons.Filled.Memory)
}

@Composable
fun DeviceScreen(
    navController: NavHostController,
    device: FireLampDevice?
) {
    val viewModel: DeviceViewModel = hiltViewModel()
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val rebootRequired by viewModel.rebootRequired.collectAsState()
    val lampState by viewModel.lampState.collectAsState()
    val effects by viewModel.effects.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val lampTime by viewModel.lampTime.collectAsState()
    val demoSettings by viewModel.demoSettings.collectAsState()
    val wifiSettings by viewModel.wifiSettings.collectAsState()
    val wifiStatus by viewModel.wifiStatus.collectAsState()
    val lampName by viewModel.deviceName.collectAsState()
    val timeZone by viewModel.timeZone.collectAsState()
    val ledSettings by viewModel.ledSettings.collectAsState()
    val hardwareSettings by viewModel.hardwareSettings.collectAsState()
    val firmwareState by viewModel.firmwareState.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(device) {
        viewModel.connectToDevice(device)
    }

    // Automatic reconnection (in the ViewModel) runs only while the screen is in the foreground
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        viewModel.setForeground(lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val reconnect by viewModel.reconnect.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            Toast.makeText(context, R.string.operation_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // The lamp name in the action bar, with the firmware version once known: "FireLamp-1A2B 0.1.0"
    val deviceName = device?.getDisplayName()
    val firmwareVersion = deviceInfo?.firmwareVersion
    DisposableEffect(deviceName, firmwareVersion) {
        val activity = context as? Activity
        if (deviceName != null) activity?.title = listOfNotNull(deviceName, firmwareVersion).joinToString(" ")
        onDispose { activity?.title = context.getString(R.string.app_name) }
    }

    DeviceScreenContent(
        connectionState = connectionState,
        rebootRequired = rebootRequired,
        reconnect = reconnect,
        onRetry = viewModel::reconnectNow,
        onForgetAndRetry = viewModel::forgetBondAndReconnect,
        onReboot = viewModel::reboot,
        lampTab = {
            lampState?.let { state ->
                LampTab(
                    lampState = state,
                    effects = effects,
                    onPowerChange = viewModel::setPower,
                    onBrightnessChange = viewModel::setBrightness,
                    onSelectEffect = viewModel::selectEffect,
                    onNextEffect = viewModel::nextEffect,
                    onDemoChange = viewModel::setDemo,
                    onParamChange = viewModel::setEffectParam,
                )
            }
        },
        scheduleTab = {
            schedule?.let {
                ScheduleTab(
                    schedule = it,
                    effects = effects,
                    lampTime = lampTime,
                    onSave = viewModel::saveSchedule,
                )
            }
        },
        settingsTab = {
            // Demo flags in the cached effect list may be stale: re-read them once per opening
            LaunchedEffect(Unit) { viewModel.refreshEffects() }
            SettingsTab(
                state = SettingsTabState(
                    lampState = lampState,
                    effects = effects,
                    demoSettings = demoSettings,
                    wifiSettings = wifiSettings,
                    wifiStatus = wifiStatus,
                    deviceName = lampName,
                    lampTime = lampTime,
                    timeZone = timeZone,
                    phoneTimeZone = viewModel.phoneTimeZone,
                ),
                actions = viewModel,
            )
        },
        hardwareTab = {
            HardwareTab(
                deviceInfo = deviceInfo,
                ledSettings = ledSettings,
                hardwareSettings = hardwareSettings,
                onSaveLedSettings = viewModel::saveLedSettings,
                onSaveHardwareSettings = viewModel::saveHardwareSettings,
                onReboot = viewModel::reboot,
                firmware = {
                    deviceInfo?.let { info ->
                        FirmwareSection(
                            info = info,
                            state = firmwareState,
                            updateInfo = updateInfo,
                            wifiConfigured = wifiSettings?.let { it.mode != WifiMode.OFF && it.ssid.isNotEmpty() } == true,
                            actions = viewModel,
                        )
                    }
                },
            )
        },
    )
}

@Composable
fun DeviceScreenContent(
    connectionState: ConnectionState,
    rebootRequired: Boolean,
    reconnect: DeviceViewModel.Reconnect,
    onRetry: () -> Unit,
    onForgetAndRetry: () -> Unit,
    onReboot: () -> Unit,
    lampTab: @Composable () -> Unit = {},
    scheduleTab: @Composable () -> Unit = {},
    settingsTab: @Composable () -> Unit = {},
    hardwareTab: @Composable () -> Unit = {},
    initialTab: DeviceTab = DeviceTab.Lamp,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                DeviceTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ConnectionHeader(connectionState)
            HorizontalDivider()

            when (connectionState) {
                is ConnectionState.PairingRejected -> PairingRejectedCard(
                    staleBondRemoved = connectionState.staleBondRemoved,
                    onRetry = onRetry,
                    onForgetAndRetry = onForgetAndRetry
                )
                ConnectionState.NoPermission -> NoPermissionCard(onRetry)
                is ConnectionState.UnsupportedProtocol -> MessageCard(
                    title = stringResource(R.string.state_unsupported_protocol),
                    text = stringResource(R.string.unsupported_protocol_hint, connectionState.version)
                )
                ConnectionState.Ready -> {
                    if (rebootRequired) RebootBanner(onReboot)
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            DeviceTab.Lamp -> lampTab()
                            DeviceTab.Schedule -> scheduleTab()
                            DeviceTab.Settings -> settingsTab()
                            DeviceTab.Hardware -> hardwareTab()
                        }
                    }
                }
                // The first connection: just wait
                else -> if (reconnect.attempt == 0 && connectionState.isInProgress) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ConnectionLostCard(connectionState, reconnect, onRetry)
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeader(state: ConnectionState) {
    val text = when (state) {
        ConnectionState.Disconnected -> stringResource(R.string.state_disconnected)
        ConnectionState.Connecting -> stringResource(R.string.state_connecting)
        ConnectionState.Bonding -> stringResource(R.string.state_bonding)
        ConnectionState.Loading -> stringResource(R.string.state_loading)
        ConnectionState.Ready -> stringResource(R.string.state_ready)
        ConnectionState.Disconnecting -> stringResource(R.string.state_disconnecting)
        is ConnectionState.PairingRejected -> stringResource(R.string.state_pairing_rejected)
        is ConnectionState.UnsupportedProtocol -> stringResource(R.string.state_unsupported_protocol)
        ConnectionState.NoPermission -> stringResource(R.string.state_no_permission)
        is ConnectionState.Error -> stringResource(R.string.state_error, state.message)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (state.isInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == ConnectionState.Ready) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PairingRejectedCard(
    staleBondRemoved: Boolean,
    onRetry: () -> Unit,
    onForgetAndRetry: () -> Unit,
) {
    MessageCard(
        title = stringResource(R.string.pairing_rejected_title),
        text = stringResource(R.string.pairing_rejected_hint)
    ) {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pairing_retry))
        }
        // When the app removed the stale bond itself there is nothing left to forget
        if (!staleBondRemoved) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pairing_rejected_forget_hint),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onForgetAndRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pairing_forget_and_retry))
            }
        }
    }
}

/** The permissions were revoked in the system settings: send the user there. */
@Composable
private fun NoPermissionCard(onRetry: () -> Unit) {
    val context = LocalContext.current
    MessageCard(
        title = stringResource(R.string.state_no_permission),
        text = stringResource(R.string.no_permission_hint)
    ) {
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.no_permission_open_settings)) }
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connection_reconnect))
        }
    }
}

@Composable
private fun ConnectionLostCard(
    state: ConnectionState,
    reconnect: DeviceViewModel.Reconnect,
    onRetry: () -> Unit,
) {
    val retrying = !reconnect.exhausted
    MessageCard(
        title = stringResource(R.string.connection_lost_title),
        text = if (retrying) {
            stringResource(R.string.connection_lost_retrying, reconnect.attempt.coerceAtLeast(1), DeviceViewModel.RECONNECT_ATTEMPTS)
        } else {
            stringResource(R.string.connection_lost_hint)
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Button(
            onClick = onRetry,
            enabled = !state.isInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.connection_reconnect))
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    actions: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun RebootBanner(onReboot: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.reboot_required),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onReboot) { Text(stringResource(R.string.reboot_now)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairingRejectedPreview() {
    FireLampTheme {
        DeviceScreenContent(
            connectionState = ConnectionState.PairingRejected(staleBondRemoved = false),
            rebootRequired = false,
            reconnect = DeviceViewModel.Reconnect(),
            onRetry = {},
            onForgetAndRetry = {},
            onReboot = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadyPreview() {
    FireLampTheme {
        DeviceScreenContent(
            connectionState = ConnectionState.Ready,
            rebootRequired = true,
            reconnect = DeviceViewModel.Reconnect(),
            onRetry = {},
            onForgetAndRetry = {},
            onReboot = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionLostPreview() {
    FireLampTheme {
        DeviceScreenContent(
            connectionState = ConnectionState.Disconnected,
            rebootRequired = false,
            reconnect = DeviceViewModel.Reconnect(attempt = 3, exhausted = true),
            onRetry = {},
            onForgetAndRetry = {},
            onReboot = {},
        )
    }
}
