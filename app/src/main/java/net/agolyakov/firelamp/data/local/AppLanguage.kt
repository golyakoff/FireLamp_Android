package net.agolyakov.firelamp.data.local

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val tag: String) {
    English("en"),
    Russian("ru")
}

/**
 * The in-app language choice. Kept apart from [FireLampPreferences], whose file holds one
 * entry per device MAC address and is read back as a whole.
 *
 * Read from `MainActivity.attachBaseContext`, i.e. before Hilt can inject anything, so this
 * takes a plain [Context] rather than being injected there.
 */
class LanguagePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() {
            val tag = prefs.getString(KEY_LANGUAGE, null)
            return AppLanguage.entries.firstOrNull { it.tag == tag } ?: systemLanguage()
        }
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.tag) }

    /**
     * Until the user picks one: the system language if we speak it, English otherwise.
     *
     * Read from the system resources rather than [Locale.getDefault], which
     * [withAppLanguage] overrides with the in-app choice.
     */
    private fun systemLanguage(): AppLanguage {
        val tag = Resources.getSystem().configuration.locales[0].language
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.English
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
    }
}

/**
 * A context whose resources resolve in [language], regardless of the system locale.
 *
 * Also sets the JVM default locale, so that number and date formatting done outside of any
 * context (`String.format` and friends) follows the same choice.
 */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}
