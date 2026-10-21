package net.agolyakov.firelamp.ui.screen.device

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.DeviceInfo
import net.agolyakov.firelamp.protocol.OtaError
import net.agolyakov.firelamp.protocol.UpdateCheckState
import net.agolyakov.firelamp.protocol.UpdateInfo
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater.Reason
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater.State
import net.agolyakov.firelamp.service.firmware.otaErrorOf
import net.agolyakov.firelamp.ui.component.SettingsSection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

interface FirmwareActions {
    fun check()
    fun installFromPhone(available: State.Available)
    fun askLampToCheck()
    fun installByLamp(version: String?)
    fun cancel()
}

/**
 * Firmware update card of the Hardware tab: the phone checks the release manifest and uploads
 * over BLE, or the lamp updates itself over WiFi. A running update is shown in a dialog that
 * cannot be dismissed (cancel is possible while the phone uploads).
 */
@Composable
fun FirmwareSection(
    info: DeviceInfo,
    state: State,
    updateInfo: UpdateInfo?,
    wifiConfigured: Boolean,
    actions: FirmwareActions,
) {
    // The lamp can update itself only with WiFi set up (Command 0x06/0x07 fail otherwise)
    val lampCanUpdate = info.internetUpdateSupported && wifiConfigured
    val lampFound = updateInfo?.takeIf { it.state == UpdateCheckState.AVAILABLE }?.latestVersion

    SettingsSection(stringResource(R.string.fw_title)) {
        Text(stringResource(R.string.fw_current, info.firmwareVersion), style = MaterialTheme.typography.bodyLarge)

        when (state) {
            is State.UpToDate -> Text(stringResource(R.string.fw_up_to_date), color = MaterialTheme.colorScheme.primary)
            is State.NoOfficialBuild -> Hint(stringResource(R.string.fw_no_official_build, state.buildKey ?: "?", state.latestVersion))
            is State.Available -> Text(
                stringResource(R.string.fw_available, state.version),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            is State.Failed -> Text(failureText(state), color = MaterialTheme.colorScheme.error)
            is State.Done -> Text(stringResource(R.string.fw_done, state.version), color = MaterialTheme.colorScheme.primary)
            is State.RolledBack -> Text(stringResource(R.string.fw_rolled_back, state.version), color = MaterialTheme.colorScheme.error)
            else -> {}
        }

        // What the lamp found itself on its last WiFi session
        updateInfo?.let { LampCheckLine(it) }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is State.Available) {
                Button(onClick = { actions.installFromPhone(state) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.fw_install_from_phone))
                }
            } else {
                OutlinedButton(onClick = actions::check, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.fw_check))
                }
            }
            if (lampCanUpdate) {
                val version = (state as? State.Available)?.version ?: lampFound
                if (version != null) {
                    OutlinedButton(
                        onClick = { actions.installByLamp(version) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.fw_install_by_lamp)) }
                } else {
                    TextButton(onClick = actions::askLampToCheck, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fw_ask_lamp_to_check))
                    }
                }
            }
        }
        Hint(stringResource(if (lampCanUpdate) R.string.fw_hint_both else R.string.fw_hint_phone_only))
    }

    if (state.isBusy) ProgressDialog(state, actions::cancel)
}

@Composable
private fun LampCheckLine(info: UpdateInfo) {
    val text = when (info.state) {
        UpdateCheckState.AVAILABLE -> stringResource(R.string.fw_lamp_found, info.latestVersion)
        UpdateCheckState.UP_TO_DATE -> stringResource(R.string.fw_lamp_up_to_date)
        UpdateCheckState.CHECKING -> stringResource(R.string.fw_lamp_checking)
        UpdateCheckState.FAILED -> stringResource(R.string.fw_lamp_check_failed)
        UpdateCheckState.NOT_CHECKED, UpdateCheckState.UNKNOWN -> return
    }
    val time = info.lastCheckEpochSeconds.takeIf { it > 0 }?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()))
    }
    Hint(listOfNotNull(text, time).joinToString(" · "))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun failureText(state: State.Failed): String = when (state.reason) {
    Reason.NO_INTERNET -> stringResource(R.string.fw_error_no_internet)
    Reason.BAD_FILE -> stringResource(R.string.fw_error_bad_file)
    Reason.NOT_CONNECTED -> stringResource(R.string.fw_error_not_connected)
    Reason.TIMEOUT -> stringResource(R.string.fw_error_timeout)
    Reason.NOT_VERIFIED -> stringResource(R.string.fw_error_not_verified)
    Reason.CANCELLED -> stringResource(R.string.fw_error_cancelled)
    Reason.LAMP_ERROR -> stringResource(R.string.fw_error_lamp, otaErrorOf(state.detail)?.let { stringResource(otaErrorText(it)) } ?: (state.detail ?: ""))
}

private fun otaErrorText(error: OtaError) = when (error) {
    OtaError.BUSY -> R.string.ota_error_busy
    OtaError.BAD_SIZE -> R.string.ota_error_bad_size
    OtaError.FLASH_WRITE -> R.string.ota_error_flash
    OtaError.IMAGE_INVALID -> R.string.ota_error_image
    OtaError.SHA256_MISMATCH -> R.string.ota_error_sha
    OtaError.TIMEOUT -> R.string.ota_error_timeout
    OtaError.TOO_MUCH_DATA -> R.string.ota_error_too_much
    OtaError.DOWNLOAD_FAILED -> R.string.ota_error_download
    OtaError.NONE, OtaError.UNKNOWN -> R.string.unknown
}

/** Not dismissable while the update runs; keeps the screen on (a sleeping phone may drop BLE). */
@Composable
private fun ProgressDialog(state: State, onCancel: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val (text, progress) = when (state) {
        State.Checking -> stringResource(R.string.fw_progress_checking) to null
        is State.Downloading -> stringResource(R.string.fw_progress_downloading) to state.fraction
        State.Preparing -> stringResource(R.string.fw_progress_preparing) to null
        is State.Uploading -> stringResource(R.string.fw_progress_uploading, state.sent / 1024, state.total / 1024) to
            state.sent.toFloat() / state.total
        State.Verifying -> stringResource(R.string.fw_progress_verifying) to null
        State.InternetChecking -> stringResource(R.string.fw_progress_lamp_checking) to null
        is State.InternetDownloading -> stringResource(R.string.fw_progress_lamp_downloading, state.received / 1024, state.total / 1024) to
            state.total.takeIf { it > 0 }?.let { state.received.toFloat() / it }
        is State.Rebooting -> stringResource(R.string.fw_progress_rebooting) to null
        else -> "" to null
    }
    // Only an upload from the phone can be cancelled: the lamp's own update goes on without us
    val cancellable = state is State.Downloading || state is State.Preparing || state is State.Uploading

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.fw_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    CircularProgressIndicator()
                }
                if (state is State.Uploading || state is State.Preparing || state is State.Verifying || state is State.Rebooting) {
                    Text(
                        stringResource(R.string.fw_progress_keep_close),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            if (cancellable) TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
