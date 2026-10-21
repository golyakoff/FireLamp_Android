package net.agolyakov.firelamp.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import net.agolyakov.firelamp.domain.repository.PreferencesRepository
import javax.inject.Inject

class FireLampPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {
    private val prefs = context.getSharedPreferences(
        "device_friendly_names",
        Context.MODE_PRIVATE
    )

    override fun saveFriendlyName(macAddress: String, friendlyName: String?) {
        if (friendlyName.isNullOrBlank()) {
            prefs.edit { remove(macAddress) }
        } else {
            prefs.edit { putString(macAddress, friendlyName) }
        }
    }

    override fun getFriendlyName(macAddress: String): String? {
        return prefs.getString(macAddress, null)
    }

    override fun deleteFriendlyName(macAddress: String) {
        prefs.edit { remove(macAddress) }
    }

    override fun getAllFriendlyNames(): Map<String, String> {
        return prefs.all.mapNotNull { (key, value) ->
            if (value is String) key to value else null
        }.toMap()
    }
}