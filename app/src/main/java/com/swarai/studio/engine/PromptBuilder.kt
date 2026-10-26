package com.swarai.studio.engine

import com.swarai.studio.catalog.Catalog
import com.swarai.studio.data.db.ProjectEntity
import com.swarai.studio.lyrics.LyricsTools

object PromptBuilder {
    fun intensityWord(value: Int) = when {
        value < 25 -> "gentle, low intensity"
        value < 50 -> "moderate intensity"
        value < 75 -> "strong intensity"
        else -> "very high intensity, climactic"
    }

    fun balanceWord(value: Int) = when {
        value < 30 -> "instrument-forward mix with vocals set back"
        value > 70 -> "vocal-forward mix, clear lead vocal"
        else -> ""
    }

    fun caption(project: ProjectEntity): String {
        val genre = Catalog.genre(project.genreId)
        val mood = Catalog.mood(project.moodId)
        val language = Catalog.language(project.languageId)
        val parts = mutableListOf(genre.tags, mood.tags, intensityWord(project.intensity))
        if (!project.instrumental) {
            parts += Catalog.voice(project.voiceId).tags
            parts += "${language.label} lyrics"
            balanceWord(project.vocalBalance).takeIf { it.isNotEmpty() }?.let { parts += it }
        } else {
            parts += "instrumental, no vocals"
        }
        Catalog.instrumentsFor(project.instrumentIds).takeIf { it.isNotEmpty() }?.let { list ->
            parts += "featuring " + list.joinToString(", ") { it.tag }
        }
        parts += Catalog.arrangement(project.arrangementId).tags
        project.extraPrompt.trim().takeIf { it.isNotEmpty() }?.let { parts += it }
        return parts.joinToString(", ").take(1000)
    }

    fun spec(project: ProjectEntity, format: String = "mp3"): GenerationSpec = GenerationSpec(
        caption = caption(project),
        lyrics = if (project.instrumental) "" else LyricsTools.toModelFormat(project.lyrics),
        instrumental = project.instrumental,
        languageCode = Catalog.language(project.languageId).modelCode,
        durationSec = project.durationSec,
        bpm = project.bpm,
        keyScale = project.keyScale?.takeIf { it.isNotBlank() },
        variations = project.variations.coerceIn(1, 4),
        format = format,
    )
}
