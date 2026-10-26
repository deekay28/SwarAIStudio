package com.swarai.studio

import android.content.Context
import com.swarai.studio.data.SecretStore
import com.swarai.studio.data.SettingsStore
import com.swarai.studio.data.db.AppDatabase
import com.swarai.studio.engine.EngineRegistry
import com.swarai.studio.export.ExportManager
import com.swarai.studio.generation.GenerationRepository
import com.swarai.studio.player.PlayerController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Simple manual dependency container; one per process. */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val secrets = SecretStore(app)
    val engines = EngineRegistry(settings, secrets)
    val generation = GenerationRepository(app, db)
    val player by lazy { PlayerController(app) }
    val export = ExportManager(app)
    /** For work that must finish even if the screen that started it goes away (e.g. saving a draft). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // All media lives in app-private storage; users export copies through the system file picker.
    val songsDir: File get() = File(app.filesDir, "songs").apply { mkdirs() }
    val editsDir: File get() = File(app.filesDir, "edits").apply { mkdirs() }
    val importsDir: File get() = File(app.filesDir, "imports").apply { mkdirs() }
    val coversDir: File get() = File(app.filesDir, "covers").apply { mkdirs() }
    val videosDir: File get() = File(app.filesDir, "videos").apply { mkdirs() }
}
