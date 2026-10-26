package com.swarai.studio.engine

import com.swarai.studio.data.SecretStore
import com.swarai.studio.data.SettingsStore

class EngineRegistry(private val settings: SettingsStore, private val secrets: SecretStore) {
    val ids = listOf(BackendEngine.ID, DeepInfraDirectEngine.ID, AceStepCompanionEngine.ID, OnDeviceEngine.ID)

    suspend fun engine(id: String): MusicEngine {
        val s = settings.current()
        return when (id) {
            BackendEngine.ID -> BackendEngine(s.backendUrl, secrets.get(SecretStore.BACKEND_TOKEN), s.backendProviderId)
            DeepInfraDirectEngine.ID -> DeepInfraDirectEngine(secrets.get(SecretStore.DEEPINFRA_KEY))
            AceStepCompanionEngine.ID -> AceStepCompanionEngine(s.companionUrl, secrets.get(SecretStore.COMPANION_KEY))
            else -> OnDeviceEngine()
        }
    }

    suspend fun all(): List<MusicEngine> = ids.map { engine(it) }

    /** The user's chosen engine, or the first configured one. Never silently picks a paid engine the user didn't choose. */
    suspend fun selectedId(): String = settings.current().engineId.takeIf { it in ids } ?: ""
}
