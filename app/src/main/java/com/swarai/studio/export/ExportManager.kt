package com.swarai.studio.export

import android.content.Context
import android.net.Uri
import com.swarai.studio.catalog.Catalog
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.SwarJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ProjectFile(
    val format: String = "swarai-project",
    val version: Int = 1,
    val title: String,
    val lyrics: String,
    val originalLyrics: String,
    val languageId: String,
    val genreId: String,
    val moodId: String,
    val voiceId: String,
    val instrumentIds: String,
    val arrangementId: String,
    val intensity: Int,
    val vocalBalance: Int,
    val bpm: Int? = null,
    val keyScale: String? = null,
    val durationSec: Int,
    val instrumental: Boolean,
    val extraPrompt: String,
    val songs: List<SongMeta> = emptyList(),
)

@Serializable
data class SongMeta(
    val title: String,
    val format: String,
    val durationMs: Long,
    val engine: String,
    val provider: String,
    val aiGenerated: Boolean,
    val seed: Long? = null,
    val caption: String,
    val createdAt: Long,
)

/** All exports go through the Storage Access Framework: the user picks the destination, no storage permission needed. */
class ExportManager(private val context: Context) {
    suspend fun copyToUri(source: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> source.inputStream().use { it.copyTo(out) } }
            ?: error("Could not open the chosen location for writing.")
    }

    suspend fun writeText(target: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open the chosen location for writing.")
    }

    /** Reads a user-chosen text file, rejecting anything that isn't reasonably-sized UTF-8 text. */
    suspend fun readText(source: Uri, maxBytes: Int = 512 * 1024): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(source)?.use { input ->
            val buffer = ByteArray(maxBytes + 1)
            var total = 0
            while (total <= maxBytes) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            if (total > maxBytes) throw IllegalArgumentException("That file is too large to be lyrics (limit ${maxBytes / 1024} KB).")
            buffer.copyOf(total)
        } ?: throw IllegalArgumentException("Could not open that file.")
        val decoder = Charsets.UTF_8.newDecoder()
        val text = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { throw IllegalArgumentException("That file is not UTF-8 text.") }
        if (text.count { it == '\u0000' } > 0) throw IllegalArgumentException("That file looks binary, not text.")
        text.removePrefix("﻿")
    }

    /** Copies a user-chosen audio file into app storage after checking it's really audio. */
    suspend fun importAudio(source: Uri, destDir: File): File = withContext(Dispatchers.IO) {
        val head = ByteArray(16)
        context.contentResolver.openInputStream(source)?.use { it.read(head) } ?: throw IllegalArgumentException("Could not open that file.")
        val format = com.swarai.studio.engine.sniffAudioFormat(head)
            ?: throw IllegalArgumentException("Only MP3, WAV, FLAC, OGG and M4A audio files can be imported.")
        destDir.mkdirs()
        val dest = File(destDir, "import-${System.currentTimeMillis()}.$format")
        context.contentResolver.openInputStream(source)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
        dest
    }

    fun projectFile(project: ProjectEntity, songs: List<SongEntity>) = ProjectFile(
        title = project.title, lyrics = project.lyrics, originalLyrics = project.originalLyrics,
        languageId = project.languageId, genreId = project.genreId, moodId = project.moodId, voiceId = project.voiceId,
        instrumentIds = project.instrumentIds, arrangementId = project.arrangementId, intensity = project.intensity,
        vocalBalance = project.vocalBalance, bpm = project.bpm, keyScale = project.keyScale,
        durationSec = project.durationSec, instrumental = project.instrumental, extraPrompt = project.extraPrompt,
        songs = songs.map {
            SongMeta(it.title, it.format, it.durationMs, it.engineId, it.providerName, it.isAiGenerated, it.seed, it.caption, it.createdAt)
        },
    )

    fun encodeProject(file: ProjectFile): String = SwarJson.encodeToString(ProjectFile.serializer(), file)

    fun decodeProject(text: String): ProjectEntity {
        val file = runCatching { SwarJson.decodeFromString(ProjectFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Not a SwarAI project file.") }
        require(file.format == "swarai-project") { "Not a SwarAI project file." }
        return ProjectEntity(
            title = file.title.take(200), lyrics = file.lyrics, originalLyrics = file.originalLyrics,
            languageId = file.languageId, genreId = file.genreId, moodId = file.moodId, voiceId = file.voiceId,
            instrumentIds = file.instrumentIds, arrangementId = file.arrangementId,
            intensity = file.intensity.coerceIn(0, 100), vocalBalance = file.vocalBalance.coerceIn(0, 100),
            bpm = file.bpm?.coerceIn(30, 300), keyScale = file.keyScale, durationSec = file.durationSec.coerceIn(10, 600),
            instrumental = file.instrumental, extraPrompt = file.extraPrompt,
        )
    }

    fun youtubeText(song: SongEntity): String = buildString {
        append("Title: ").append(song.ytTitle.ifBlank { song.title }).append("\n\n")
        append("Description:\n").append(song.ytDescription).append("\n\n")
        append("Tags: ").append(song.ytTags).append('\n')
    }

    companion object {
        const val AI_DISCLOSURE =
            "Music generated with AI (via SwarAI Studio). Lyrics by the channel owner. " +
                "Uploaders must mark this as altered or synthetic content where YouTube requires it."

        fun suggestDescription(project: ProjectEntity?, song: SongEntity): String {
            val genre = project?.let { Catalog.genre(it.genreId).label }
            val language = project?.let { Catalog.language(it.languageId).label }
            return buildString {
                append(song.title)
                if (genre != null) append(" · ").append(genre)
                if (language != null) append(" · ").append(language)
                append("\n\n")
                if (project != null && !project.instrumental && project.lyrics.isNotBlank()) {
                    append("Lyrics:\n").append(project.lyrics.trim()).append("\n\n")
                }
                if (song.isAiGenerated) append(AI_DISCLOSURE).append('\n')
            }
        }

        fun suggestTags(project: ProjectEntity?): String {
            if (project == null) return ""
            val genre = Catalog.genre(project.genreId)
            val language = Catalog.language(project.languageId)
            return listOf(genre.label, language.label, Catalog.mood(project.moodId).label, "${language.label} song", "AI music")
                .distinct().joinToString(", ")
        }
    }
}
