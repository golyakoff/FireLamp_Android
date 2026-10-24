package net.agolyakov.firelamp.ui.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.protocol.CalibrationState
import net.agolyakov.firelamp.protocol.PowerStatus
import net.agolyakov.firelamp.ui.component.SettingsSection

interface PowerActions {
    /** The brightness kept after brownouts back to 100 %. */
    fun restoreFullPower()
    fun startCalibration()
    /** The matrix flickers at the current step. */
    fun calibrationFails()
    fun cancelCalibration()
}

/**
 * Power supply card of the System tab (firmware 0.9.0+): the brightness the lamp lowered after
 * brownout reboots, and the calibration of the current limit. A running calibration is a dialog
 * with the step and "the matrix flickers"; a brownout reboot during it drops the connection, the
 * result comes after the reconnect.
 */
@Composable
fun PowerSection(status: PowerStatus, actions: PowerActions) {
    var confirm by remember { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.power_title)) {
        if (status.percent < 100) {
            Text(stringResource(R.string.power_lowered, status.percent), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = actions::restoreFullPower, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.power_restore))
            }
        }
        when (status.calibration) {
            CalibrationState.DONE -> Text(
                stringResource(R.string.power_calib_done, status.step, status.steps, status.limitMa),
                color = MaterialTheme.colorScheme.primary
            )
            CalibrationState.CANCELLED -> Text(stringResource(R.string.power_calib_cancelled))
            else -> {}
        }
        OutlinedButton(
            onClick = { confirm = true },
            enabled = status.calibration != CalibrationState.RUNNING,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.power_calibrate)) }
        Text(
            stringResource(R.string.power_calibrate_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.power_calibrate)) },
            text = { Text(stringResource(R.string.power_calibrate_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    actions.startCalibration()
                }) { Text(stringResource(R.string.power_calibrate_start)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    }

    if (status.calibration == CalibrationState.RUNNING) CalibrationDialog(status, actions)
}

@Composable
private fun CalibrationDialog(status: PowerStatus, actions: PowerActions) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.power_calibrate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.power_calibrating, status.step.coerceAtLeast(1), status.steps))
                LinearProgressIndicator(
                    progress = { status.step.toFloat() / status.steps.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.power_calibrating_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = actions::calibrationFails) { Text(stringResource(R.string.power_flickers)) } },
        dismissButton = { TextButton(onClick = actions::cancelCalibration) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}
