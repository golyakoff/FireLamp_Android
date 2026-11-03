package net.agolyakov.firelamp.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.agolyakov.firelamp.data.demo.FirmwareModelsRepository
import net.agolyakov.firelamp.data.local.AppLanguage
import net.agolyakov.firelamp.data.local.LanguagePreferences
import net.agolyakov.firelamp.data.repository.AppUpdateRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val languagePreferences: LanguagePreferences,
    modelsRepository: FirmwareModelsRepository,
) : ViewModel() {
    val updateState = appUpdateRepository.state
    val appVersion = appUpdateRepository.currentVersion
    val models = modelsRepository.models

    val language: AppLanguage get() = languagePreferences.language

    fun checkForUpdate() {
        viewModelScope.launch { appUpdateRepository.checkForUpdate() }
    }

    /** Saved only; the caller recreates the activity to re-resolve the resources. */
    fun setLanguage(language: AppLanguage) {
        languagePreferences.language = language
    }
}
