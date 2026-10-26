package com.swarai.studio.engine

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class GenerationSpec(
    val caption: String,
    val lyrics: String,
    val instrumental: Boolean = false,
    val languageCode: String = "hi",
    val durationSec: Int = 120,
    val bpm: Int? = null,
    val keyScale: String? = null,
    val negativePrompt: String? = null,
    val variations: Int = 1,
    val seed: Long? = null,
    val format: String = "mp3",
)

enum class EngineKind(val label: String) {
    ON_DEVICE("On this phone"),
    COMPANION("Your computer (local network)"),
    CLOUD_NOTEBOOK("Online via your own cloud notebook"),
    HOSTED_BACKEND("Online via your SwarAI server"),
    HOSTED_DIRECT("Online, direct with your own key"),
}

data class EnginePricing(val usdPerSecond: Double, val notes: String) {
    val isFree: Boolean get() = usdPerSecond <= 0.0
    fun estimate(seconds: Int): Double = seconds * usdPerSecond
}

data class EngineCapabilities(
    val minDurationSec: Int = 10,
    val maxDurationSec: Int = 600,
    val maxVariations: Int = 4,
    val formats: List<String> = listOf("mp3"),
    val vocals: Boolean = true,
    val instrumental: Boolean = true,
    /** "supported", "abandon_only" (stops waiting; provider may still bill), or "none" */
    val cancel: String = "abandon_only",
    val editOperations: List<String> = emptyList(),
    val languagesNote: String = "",
)

data class EngineInfo(
    val id: String,
    val name: String,
    val kind: EngineKind,
    val model: String,
    /** Plain statement of where lyrics/audio go when generating. */
    val sendsDataTo: String,
    val available: Boolean,
    val unavailableReason: String? = null,
    val isAiMusic: Boolean = true,
    val license: String = "",
    val pricing: EnginePricing = EnginePricing(0.0, ""),
    val capabilities: EngineCapabilities = EngineCapabilities(),
) {
    val worksWithoutInternet: Boolean get() = kind == EngineKind.ON_DEVICE || kind == EngineKind.COMPANION

    fun clampDuration(seconds: Int) = seconds.coerceIn(capabilities.minDurationSec, capabilities.maxDurationSec)

    fun estimateUsd(spec: GenerationSpec): Double =
        pricing.estimate(clampDuration(spec.durationSec) * spec.variations)
}

data class GeneratedAudio(
    val file: File,
    val format: String,
    val seed: Long?,
    val durationSec: Double?,
    val lyricsUsed: String?,
)

interface GenerationCallbacks {
    /** Called once the provider accepted the job; the token lets a restarted worker resume instead of re-billing. */
    suspend fun onSubmitted(resumeToken: String)
    suspend fun onProgress(message: String)
}

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface MusicEngine {
    val id: String
    /** True when an interrupted job can be resumed from its resume token without submitting again. */
    val resumable: Boolean

    suspend fun describe(): EngineInfo
    suspend fun testConnection(): String
    suspend fun generate(
        spec: GenerationSpec,
        outputDir: File,
        resumeToken: String?,
        callbacks: GenerationCallbacks,
    ): List<GeneratedAudio>
}
