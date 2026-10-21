package net.agolyakov.firelamp.domain.repository

interface PreferencesRepository {
    fun saveFriendlyName(macAddress: String, friendlyName: String?)
    fun getFriendlyName(macAddress: String): String?
    fun getAllFriendlyNames(): Map<String, String>

    fun deleteFriendlyName(macAddress: String)
}