package net.agolyakov.firelamp.ui.screen.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.agolyakov.firelamp.R
import net.agolyakov.firelamp.data.local.AppLanguage
import net.agolyakov.firelamp.data.repository.AppUpdateRepository
import net.agolyakov.firelamp.ui.component.RadioRow
import net.agolyakov.firelamp.ui.component.SettingsSection

/** The app's own settings: about and updates, language, and the developer demo of the lamp. */
@Composable
fun SettingsScreen(
    onOpenDemo: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { AboutSection(viewModel.appVersion, updateState, viewModel::checkForUpdate) }
        item {
            SettingsSection(stringResource(R.string.settings_language)) {
                RadioRow(
                    label = stringResource(R.string.lang_russian),
                    selected = viewModel.language == AppLanguage.Russian,
                    onClick = {
                        viewModel.setLanguage(AppLanguage.Russian)
                        (context as? Activity)?.recreate()
                    },
                )
                RadioRow(
                    label = stringResource(R.string.lang_english),
                    selected = viewModel.language == AppLanguage.English,
                    onClick = {
                        viewModel.setLanguage(AppLanguage.English)
                        (context as? Activity)?.recreate()
                    },
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.settings_developers)) {
                Text(
                    stringResource(R.string.settings_developers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                viewModel.models.forEach { model ->
                    OutlinedButton(
                        onClick = { onOpenDemo(model.version) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_developers_open, model.version, model.effectsCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    version: String,
    state: AppUpdateRepository.UpdateCheckState,
    onCheck: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsSection(stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.app_version, version), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = when (state) {
                AppUpdateRepository.UpdateCheckState.Checking -> stringResource(R.string.settings_checking)
                AppUpdateRepository.UpdateCheckState.UpToDate -> stringResource(R.string.settings_up_to_date)
                is AppUpdateRepository.UpdateCheckState.UpdateAvailable ->
                    stringResource(R.string.app_update_available, state.version)
                is AppUpdateRepository.UpdateCheckState.Error ->
                    stringResource(R.string.settings_check_failed)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state is AppUpdateRepository.UpdateCheckState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        if (state is AppUpdateRepository.UpdateCheckState.UpdateAvailable) {
            Button(
                onClick = { uriHandler.openUri(state.releaseUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.app_update_download)) }
        }
        if (state != AppUpdateRepository.UpdateCheckState.Checking) {
            TextButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_check))
            }
        }
    }
}
