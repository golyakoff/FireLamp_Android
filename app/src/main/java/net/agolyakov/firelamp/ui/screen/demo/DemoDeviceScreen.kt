package net.agolyakov.firelamp.ui.screen.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.data.model.ble.ConnectionState
import net.agolyakov.firelamp.protocol.Controls
import net.agolyakov.firelamp.ui.component.SettingsSection
import net.agolyakov.firelamp.ui.screen.device.DemoSettingsSheet
import net.agolyakov.firelamp.ui.screen.device.DeviceScreenContent
import net.agolyakov.firelamp.ui.screen.device.DeviceViewModel
import net.agolyakov.firelamp.ui.screen.device.LampTab
import net.agolyakov.firelamp.ui.screen.device.PowerSection
import net.agolyakov.firelamp.ui.screen.device.ScheduleTab
import net.agolyakov.firelamp.ui.screen.device.SettingsTab
import net.agolyakov.firelamp.ui.screen.device.SettingsTabState
import net.agolyakov.firelamp.ui.screen.device.SystemTab
import net.agolyakov.firelamp.ui.screen.firmware.FirmwareUpdateMode
import net.agolyakov.firelamp.ui.screen.firmware.UpdateAction
import net.agolyakov.firelamp.ui.screen.firmware.UpdateProgressScreen
import net.agolyakov.firelamp.ui.screen.firmware.UpdateResultScreen
import net.agolyakov.firelamp.ui.screen.firmware.UpdateScreenFrame

/**
 * A lamp of a bundled firmware snapshot, without Bluetooth: all four tabs work on a local state.
 * The firmware update card runs the standard update screens with a simulated 10 s progress.
 */
@Composable
fun DemoDeviceScreen(
    viewModel: DemoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val update by viewModel.update.collectAsState()

    update?.let { demoUpdate ->
        DemoUpdateScreen(demoUpdate, viewModel::finishDemoUpdate)
        return
    }

    DeviceScreenContent(
        connectionState = ConnectionState.Ready,
        rebootRequired = false,
        reconnect = DeviceViewModel.Reconnect(),
        onRetry = {},
        onForgetAndRetry = {},
        onReboot = viewModel::reboot,
        lampTab = {
            LampTab(
                lampState = state.lampState,
                effects = state.effects,
                onPowerChange = viewModel::setPower,
                onBrightnessChange = viewModel::setBrightness,
                onSelectEffect = viewModel::selectEffect,
                onNextEffect = viewModel::nextEffect,
                onDemoChange = viewModel::setDemo,
                onParamChange = viewModel::setEffectParam,
                onFpsChange = viewModel::setFps,
                demoSettingsSheet = { onDismiss ->
                    DemoSettingsSheet(
                        saved = state.demoSettings,
                        effects = state.effects,
                        currentEffectCode = state.lampState.effectCode,
                        onSave = viewModel::saveDemoSettings,
                        onSetIncluded = viewModel::setDemoIncluded,
                        onDismiss = onDismiss,
                    )
                },
            )
        },
        scheduleTab = {
            ScheduleTab(
                schedule = state.schedule,
                effects = state.effects,
                lampTime = state.lampTime,
                onSave = viewModel::saveSchedule,
            )
        },
        settingsTab = {
            SettingsTab(
                state = SettingsTabState(
                    wifiSettings = state.wifi,
                    wifiStatus = state.wifiStatus,
                    wifiScan = state.wifiScan,
                    wifiScanSupported = true,
                    deviceName = state.deviceName,
                    lampTime = state.lampTime,
                    timeZone = state.timeZone,
                    phoneTimeZone = viewModel.phoneTimeZone,
                ),
                actions = viewModel,
            )
        },
        systemTab = {
            SystemTab(
                deviceInfo = state.info,
                ledSettings = state.led,
                hardwareSettings = state.hardware,
                onSaveLedSettings = viewModel::saveLedSettings,
                onSaveHardwareSettings = { controls: Controls, activeHigh: Boolean, gpio: Int? ->
                    viewModel.saveHardwareSettings(controls, activeHigh, gpio)
                },
                onReboot = viewModel::reboot,
                onResetEffects = viewModel::resetEffects,
                onFactoryReset = viewModel::factoryReset,
                bondedPhones = state.bonds,
                onForgetBond = viewModel::forgetBond,
                firmware = { DemoFirmwareCard(state.info.firmwareVersion, viewModel::startDemoUpdate) },
                power = { PowerSection(state.power, viewModel) },
            )
        },
    )
}

@Composable
private fun DemoFirmwareCard(
    version: String,
    onStart: (FirmwareUpdateMode) -> Unit,
) {
    SettingsSection(stringResource(R.string.fw_title)) {
        Text(stringResource(R.string.fw_current, version))
        Text(
            stringResource(R.string.demo_update_hint),
            color = MaterialTheme.colorScheme.outline,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onStart(FirmwareUpdateMode.PHONE) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fw_install_from_phone))
            }
            OutlinedButton(onClick = { onStart(FirmwareUpdateMode.LAMP) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fw_install_by_lamp))
            }
        }
    }
}

@Composable
private fun DemoUpdateScreen(update: DemoUpdate, onDone: () -> Unit) {
    val (mode, progress) = when (update) {
        is DemoUpdate.Running -> update.mode to update.progress
        is DemoUpdate.Done -> update.mode to 100f
    }
    val title = stringResource(
        if (mode == FirmwareUpdateMode.PHONE) R.string.demo_update_phone_title
        else R.string.demo_update_lamp_title
    )
    UpdateScreenFrame {
        if (update is DemoUpdate.Done) {
            UpdateResultScreen(
                success = true,
                title = stringResource(R.string.demo_update_done),
                message = stringResource(R.string.demo_update_note),
                primary = UpdateAction(stringResource(R.string.demo_update_got_it), onDone),
            )
        } else {
            UpdateProgressScreen(
                title = title,
                progress = progress,
                detail = stringResource(R.string.demo_update_note),
            )
        }
    }
}
