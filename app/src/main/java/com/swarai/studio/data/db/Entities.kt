package com.swarai.studio.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A song idea: lyrics plus the musical choices used to generate it. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lyrics: String = "",
    /** The lyrics exactly as first pasted/imported; never overwritten by edits. */
    val originalLyrics: String = "",
    val languageId: String = "hi",
    val genreId: String = "bhojpuri_folk",
    val moodId: String = "joyful",
    val voiceId: String = "folk_female_warm",
    val instrumentIds: String = "",
    val arrangementId: String = "full_band",
    val intensity: Int = 60,
    val vocalBalance: Int = 50,
    val bpm: Int? = null,
    val keyScale: String? = null,
    val durationSec: Int = 120,
    val instrumental: Boolean = false,
    val extraPrompt: String = "",
    val variations: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** An audio file the app owns (stored in app-private storage). */
@Entity(
    tableName = "songs",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("projectId"), Index("voicePreviewId")],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val title: String,
    val filePath: String,
    val format: String,
    val durationMs: Long = 0,
    /** "generated", "edited", "imported", "video" */
    val sourceKind: String = "generated",
    val parentSongId: Long? = null,
    val engineId: String = "",
    val providerName: String = "",
    val isAiGenerated: Boolean = true,
    val seed: Long? = null,
    val caption: String = "",
    val lyricsUsed: String = "",
    val favorite: Boolean = false,
    val voicePreviewId: String? = null,
    val ytTitle: String = "",
    val ytDescription: String = "",
    val ytTags: String = "",
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val genreId: String,
    val moodId: String,
    val voiceId: String,
    val instrumentIds: String,
    val arrangementId: String,
    val intensity: Int,
    val vocalBalance: Int,
    val bpm: Int?,
    val durationSec: Int,
    val extraPrompt: String,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One generation request. Persisted so a job survives the app closing and is never silently re-billed. */
@Entity(tableName = "generation_jobs", indices = [Index("projectId")])
data class GenerationJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long?,
    val engineId: String,
    val engineName: String,
    val specJson: String,
    /** QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED */
    val status: String = "QUEUED",
    val progress: String = "",
    val remoteJobId: String? = null,
    val error: String? = null,
    val estimatedCostUsd: Double = 0.0,
    val voicePreviewId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)
