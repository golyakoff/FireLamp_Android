package net.agolyakov.firelamp.ui.screen.firmware

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.OtaError
import net.agolyakov.firelamp.protocol.UpdateCheckState
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater.Reason
import net.agolyakov.firelamp.service.firmware.FirmwareUpdater.State
import net.agolyakov.firelamp.service.firmware.otaErrorOf
import java.text.NumberFormat

/**
 * The firmware update, whichever way it goes: the same question, progress and result screens.
 * [mode] only chooses what the primary button starts.
 */
@Composable
fun FirmwareScreen(
    mode: FirmwareUpdateMode,
    onBack: () -> Unit,
    viewModel: FirmwareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val info by viewModel.deviceInfo.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // The lamp may already know about an update the phone check has not found yet
        val lampKnows = mode == FirmwareUpdateMode.LAMP && updateInfo?.state == UpdateCheckState.AVAILABLE
        if (state is State.Idle && !lampKnows) viewModel.check()
    }

    // A sleeping phone may drop the BLE link during an upload
    DisposableEffect(state) {
        val window = (context as? Activity)?.window
        if (state.isBusy) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val back = UpdateAction(stringResource(R.string.dialog_back), onBack)
    val close = UpdateAction(stringResource(R.string.dialog_cancel), viewModel::cancel)
    // The lamp checks its own release: it may find one the phone does not offer
    val lampFound = updateInfo?.takeIf { it.state == UpdateCheckState.AVAILABLE }?.latestVersion
    val lampPrompt = mode == FirmwareUpdateMode.LAMP && lampFound != null &&
        (state is State.Idle || state is State.UpToDate || state is State.NoOfficialBuild || state is State.Failed)

    UpdateScreenFrame {
        if (lampPrompt) {
            UpdatePromptScreen(
                title = stringResource(R.string.fw_available_title),
                lines = listOf(
                    stringResource(R.string.fw_current, info?.firmwareVersion ?: ""),
                    stringResource(R.string.fw_new_version, lampFound ?: ""),
                ),
                primary = UpdateAction(stringResource(R.string.fw_install_by_lamp)) {
                    viewModel.installByLamp(lampFound)
                },
                secondary = back,
            )
        } else when (val s = state) {
            State.Idle, State.Checking -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_checking),
            )

            is State.UpToDate -> UpdateResultScreen(
                success = true,
                title = stringResource(R.string.fw_up_to_date),
                message = stringResource(R.string.fw_current, s.version),
                primary = back,
            )

            is State.NoOfficialBuild -> UpdateResultScreen(
                success = false,
                title = stringResource(R.string.fw_no_official_build_title),
                message = stringResource(R.string.fw_no_official_build, s.buildKey ?: "?", s.latestVersion),
                primary = back,
            )

            is State.Available -> UpdatePromptScreen(
                title = stringResource(R.string.fw_available_title),
                lines = listOf(
                    stringResource(R.string.fw_current, info?.firmwareVersion ?: ""),
                    stringResource(R.string.fw_new_version, s.version),
                ),
                primary = UpdateAction(
                    stringResource(
                        if (mode == FirmwareUpdateMode.PHONE) R.string.fw_install_from_phone
                        else R.string.fw_install_by_lamp
                    )
                ) {
                    if (mode == FirmwareUpdateMode.PHONE) viewModel.installFromPhone(s)
                    else viewModel.installByLamp(s.version)
                },
                secondary = back,
            )

            is State.Downloading -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_downloading),
                progress = s.fraction * 100f,
                detail = stringResource(R.string.fw_progress_downloading_detail),
                close = close,
            )

            State.Preparing -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_preparing),
                detail = stringResource(R.string.fw_progress_keep_close),
                close = close,
            )

            is State.Uploading -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_uploading),
                progress = percent(s.sent, s.total),
                detail = stringResource(R.string.fw_progress_amount, kilobytes(s.sent), kilobytes(s.total)),
                note = stringResource(R.string.fw_progress_keep_close),
                close = close,
            )

            State.Verifying -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_verifying),
                detail = stringResource(R.string.fw_progress_keep_close),
            )

            State.InternetChecking -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_lamp_checking),
                note = stringResource(R.string.fw_progress_lamp_note),
                close = back,
            )

            is State.InternetDownloading -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_lamp_downloading),
                progress = percent(s.received, s.total),
                detail = if (s.total > 0) {
                    stringResource(R.string.fw_progress_amount, kilobytes(s.received), kilobytes(s.total))
                } else null,
                note = stringResource(R.string.fw_progress_lamp_note),
                close = back,
            )

            is State.Rebooting -> UpdateProgressScreen(
                title = stringResource(R.string.fw_progress_rebooting),
                note = stringResource(R.string.fw_progress_lamp_note),
                close = back,
            )

            is State.Done -> UpdateResultScreen(
                success = true,
                title = stringResource(R.string.fw_done_title),
                message = stringResource(R.string.fw_done, s.version),
                primary = UpdateAction(stringResource(R.string.fw_done_action), onBack),
            )

            is State.RolledBack -> UpdateResultScreen(
                success = false,
                title = stringResource(R.string.fw_rolled_back_title),
                message = stringResource(R.string.fw_rolled_back, s.version),
                primary = back,
            )

            is State.Failed -> UpdateResultScreen(
                success = false,
                title = stringResource(R.string.fw_failed_title),
                message = failureText(s),
                primary = UpdateAction(stringResource(R.string.fw_retry)) { viewModel.check() },
                secondary = back,
            )
        }
    }

}

private fun percent(done: Long, total: Long): Float? =
    if (total <= 0) null else (done.toFloat() * 100f / total).coerceIn(0f, 100f)

private fun kilobytes(bytes: Long): String = NumberFormat.getIntegerInstance().format(bytes / 1024)

@Composable
private fun failureText(state: State.Failed): String = when (state.reason) {
    Reason.NO_INTERNET -> stringResource(R.string.fw_error_no_internet)
    Reason.BAD_FILE -> stringResource(R.string.fw_error_bad_file)
    Reason.NOT_CONNECTED -> stringResource(R.string.fw_error_not_connected)
    Reason.TIMEOUT -> stringResource(R.string.fw_error_timeout)
    Reason.NOT_VERIFIED -> stringResource(R.string.fw_error_not_verified)
    Reason.CANCELLED -> stringResource(R.string.fw_error_cancelled)
    Reason.LAMP_ERROR -> stringResource(
        R.string.fw_error_lamp,
        otaErrorOf(state.detail)?.let { stringResource(otaErrorText(it)) } ?: (state.detail ?: "")
    )
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
