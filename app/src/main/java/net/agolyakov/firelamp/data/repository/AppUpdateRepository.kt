package net.agolyakov.firelamp.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.agolyakov.firelamp.BuildConfig
import net.agolyakov.firelamp.data.remote.api.GithubApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the releases of the app's own repository. The result is shared by the home screen (its
 * bottom update plaque), the gear badge and the settings screen. There is no in-app install: the
 * UI only offers a link to the release page, where the APK is downloaded manually.
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val githubApiService: GithubApiService
) {
    sealed class UpdateCheckState {
        object Checking : UpdateCheckState()
        object UpToDate : UpdateCheckState()
        data class UpdateAvailable(val version: String, val releaseUrl: String) : UpdateCheckState()
        data class Error(val message: String) : UpdateCheckState()
    }

    /** Display form, e.g. `v0.1.1` — matches the `vX.Y.Z` release tags. */
    val currentVersion: String = "v${BuildConfig.VERSION_NAME}"

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Checking)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    suspend fun checkForUpdate(): UpdateCheckState {
        _state.value = UpdateCheckState.Checking
        val result = withContext(Dispatchers.IO) {
            try {
                val release = githubApiService.getLatestRelease(OWNER, REPO)
                if (isNewerThanCurrent(release.tagName)) {
                    UpdateCheckState.UpdateAvailable(release.tagName, release.htmlUrl)
                } else {
                    UpdateCheckState.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "App update check failed", e)
                UpdateCheckState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
        _state.value = result
        return result
    }

    private fun isNewerThanCurrent(tagName: String): Boolean {
        val latest = parseVersion(tagName) ?: return false
        val current = parseVersion(currentVersion) ?: return false
        for (i in 0 until maxOf(latest.size, current.size)) {
            val latestPart = latest.getOrElse(i) { 0 }
            val currentPart = current.getOrElse(i) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    /** `v0.2.1` / `0.2.1-rc1` -> [0, 2, 1]; null when the version is not numeric dot-separated. */
    private fun parseVersion(version: String): List<Int>? {
        val parts = version.trimStart('v', 'V').substringBefore('-').split('.')
        return parts.map { it.toIntOrNull() ?: return null }
    }

    companion object {
        private const val TAG = "AppUpdateRepository"
        private const val OWNER = "golyakoff"
        private const val REPO = "FireLamp_Android"
    }
}
