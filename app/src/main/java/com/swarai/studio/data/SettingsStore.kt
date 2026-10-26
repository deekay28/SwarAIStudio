package com.swarai.studio.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swarai.studio.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val engineId: String = "",
    val backendUrl: String = "",
    val backendProviderId: String = "",
    val companionUrl: String = "",
    val costsAcknowledged: Boolean = false,
    val onboardingDone: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val engine = stringPreferencesKey("engine")
        val backendUrl = stringPreferencesKey("backend_url")
        val backendProvider = stringPreferencesKey("backend_provider")
        val companionUrl = stringPreferencesKey("companion_url")
        val costsAck = booleanPreferencesKey("costs_ack")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        engineId = this[Keys.engine] ?: "",
        backendUrl = this[Keys.backendUrl] ?: "",
        backendProviderId = this[Keys.backendProvider] ?: "",
        companionUrl = this[Keys.companionUrl] ?: "",
        costsAcknowledged = this[Keys.costsAck] ?: false,
        onboardingDone = this[Keys.onboarding] ?: false,
    )

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setEngine(id: String) = context.dataStore.edit { it[Keys.engine] = id }
    suspend fun setBackendUrl(url: String) = context.dataStore.edit { it[Keys.backendUrl] = url.trim() }
    suspend fun setBackendProvider(id: String) = context.dataStore.edit { it[Keys.backendProvider] = id }
    suspend fun setCompanionUrl(url: String) = context.dataStore.edit { it[Keys.companionUrl] = url.trim() }
    suspend fun setCostsAcknowledged(value: Boolean) = context.dataStore.edit { it[Keys.costsAck] = value }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
}
