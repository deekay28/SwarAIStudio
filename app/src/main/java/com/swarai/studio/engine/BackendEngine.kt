package com.swarai.studio.engine

import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class BackendPricing(val unit: String = "", @SerialName("usd_per_unit") val usdPerUnit: Double = 0.0, val notes: String = "")

@Serializable
data class BackendCapabilities(
    val vocals: Boolean = true,
    val instrumental: Boolean = true,
    @SerialName("lyrics_languages") val lyricsLanguages: String = "",
    @SerialName("min_duration_seconds") val minDuration: Int = 10,
    @SerialName("max_duration_seconds") val maxDuration: Int = 600,
    @SerialName("max_variations") val maxVariations: Int = 4,
    val cancel: String = "best_effort",
    val formats: List<String> = listOf("mp3"),
    @SerialName("edit_operations") val editOperations: List<String> = emptyList(),
)

@Serializable
data class BackendProvider(
    val id: String,
    val name: String,
    val model: String,
    val hosting: String,
    @SerialName("sends_data_to") val sendsDataTo: String,
    val license: String = "",
    @SerialName("is_ai_music") val isAiMusic: Boolean = true,
    val pricing: BackendPricing = BackendPricing(),
    val capabilities: BackendCapabilities = BackendCapabilities(),
)

@Serializable
data class BackendRequest(
    val provider: String?,
    val caption: String,
    val lyrics: String,
    val instrumental: Boolean,
    val language: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    val bpm: Int? = null,
    @SerialName("key_scale") val keyScale: String? = null,
    @SerialName("negative_prompt") val negativePrompt: String? = null,
    val variations: Int,
    val seed: Long? = null,
    @SerialName("audio_format") val audioFormat: String,
)

@Serializable
data class BackendOutput(
    val index: Int,
    val status: String,
    @SerialName("audio_path") val audioPath: String? = null,
    val format: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val seed: Long? = null,
    @SerialName("lyrics_used") val lyricsUsed: String? = null,
    val error: String? = null,
)

@Serializable
data class BackendJob(
    val id: String,
    val provider: String,
    val status: String,
    @SerialName("estimated_cost_usd") val estimatedCostUsd: Double = 0.0,
    val outputs: List<BackendOutput> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class BackendError(val detail: String? = null)

/** Talks to the SwarAI FastAPI backend, which holds the provider credentials server-side. */
class BackendEngine(
    private val baseUrlRaw: String,
    private val token: String,
    private val providerId: String?,
    private val http: OkHttpClient = Http.client,
    private val pollIntervalMs: Long = 3000,
) : MusicEngine {
    override val id = ID
    override val resumable = true
    private var cachedProviders: List<BackendProvider>? = null

    private val baseUrl: String get() = NetworkPolicy.validateBaseUrl(baseUrlRaw, allowLocalHttp = true)

    private fun request(path: String) = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")

    private suspend fun <T> call(builder: Request.Builder, parse: (String) -> T): T {
        if (baseUrlRaw.isBlank()) throw EngineException("Set your SwarAI server address in Settings.")
        if (token.isBlank()) throw EngineException("Set your SwarAI server access token in Settings.")
        val response = http.send(builder.build())
        val code = response.code
        val body = response.bodyString()
        if (code !in 200..299) {
            val detail = runCatching { SwarJson.decodeFromString(BackendError.serializer(), body).detail }.getOrNull() ?: ""
            throw httpError("SwarAI server", code, detail)
        }
        return parse(body)
    }

    suspend fun providers(): List<BackendProvider> = cachedProviders ?: call(request("/v1/providers").get()) {
        SwarJson.decodeFromString(ListSerializer(BackendProvider.serializer()), it)
    }.also { cachedProviders = it }

    override suspend fun describe(): EngineInfo {
        val base = EngineInfo(
            id = ID, name = "SwarAI server", kind = EngineKind.HOSTED_BACKEND, model = "Configured on your server",
            sendsDataTo = "Your SwarAI server, then the provider it is configured with", available = false,
        )
        if (baseUrlRaw.isBlank() || token.isBlank()) {
            return base.copy(unavailableReason = "Add your SwarAI server address and token in Settings.")
        }
        val list = runCatching { providers() }.getOrElse {
            return base.copy(unavailableReason = it.message ?: "Could not reach the server.")
        }
        val provider = list.firstOrNull { it.id == providerId } ?: list.firstOrNull()
            ?: return base.copy(unavailableReason = "The server has no music provider configured.")
        return EngineInfo(
            id = ID,
            name = provider.name,
            kind = EngineKind.HOSTED_BACKEND,
            model = provider.model,
            sendsDataTo = "Your SwarAI server → ${provider.sendsDataTo}",
            available = true,
            isAiMusic = provider.isAiMusic,
            license = provider.license,
            pricing = EnginePricing(provider.pricing.usdPerUnit, provider.pricing.notes),
            capabilities = EngineCapabilities(
                minDurationSec = provider.capabilities.minDuration,
                maxDurationSec = provider.capabilities.maxDuration,
                maxVariations = provider.capabilities.maxVariations,
                formats = provider.capabilities.formats,
                vocals = provider.capabilities.vocals,
                instrumental = provider.capabilities.instrumental,
                cancel = provider.capabilities.cancel,
                editOperations = provider.capabilities.editOperations,
                languagesNote = provider.capabilities.lyricsLanguages,
            ),
        )
    }

    override suspend fun testConnection(): String {
        cachedProviders = null
        val list = providers()
        return if (list.isEmpty()) "Connected, but the server has no provider configured."
        else "Connected. Providers: " + list.joinToString { it.name }
    }

    override suspend fun generate(
        spec: GenerationSpec,
        outputDir: File,
        resumeToken: String?,
        callbacks: GenerationCallbacks,
    ): List<GeneratedAudio> {
        val info = describe()
        if (!info.available) throw EngineException(info.unavailableReason ?: "Server unavailable")
        val format = if (spec.format in info.capabilities.formats) spec.format else info.capabilities.formats.first()
        val jobId = resumeToken ?: run {
            val body = BackendRequest(
                provider = providerId?.takeIf { it.isNotBlank() },
                caption = spec.caption,
                lyrics = spec.lyrics,
                instrumental = spec.instrumental,
                language = spec.languageCode,
                durationSeconds = info.clampDuration(spec.durationSec),
                bpm = spec.bpm,
                keyScale = spec.keyScale,
                negativePrompt = spec.negativePrompt,
                variations = spec.variations.coerceAtMost(info.capabilities.maxVariations),
                seed = spec.seed,
                audioFormat = format,
            )
            val json = SwarJson.encodeToString(BackendRequest.serializer(), body)
            val job = call(request("/v1/generations").post(json.toRequestBody(JSON))) {
                SwarJson.decodeFromString(BackendJob.serializer(), it)
            }
            callbacks.onSubmitted(job.id)
            job.id
        }
        try {
            var job = BackendJob(jobId, "", "queued")
            while (true) {
                job = call(request("/v1/generations/$jobId").get()) { SwarJson.decodeFromString(BackendJob.serializer(), it) }
                val done = job.outputs.count { it.status == "succeeded" }
                callbacks.onProgress("${job.status.replaceFirstChar { it.uppercase() }} · $done/${job.outputs.size} ready")
                if (job.status !in setOf("queued", "running")) break
                delay(pollIntervalMs)
            }
            if (job.status != "succeeded") throw EngineException(job.error ?: "Generation ${job.status}.")
            val results = job.outputs.filter { it.status == "succeeded" && it.audioPath != null }.map { output ->
                val ext = output.format ?: format
                val file = File(outputDir, "gen-$jobId-${output.index}.$ext")
                val response = http.send(request(output.audioPath!!).get().build())
                if (!response.isSuccessful) throw httpError("SwarAI server", response.code).also { response.close() }
                response.saveTo(file)
                GeneratedAudio(file, ext, output.seed, output.durationSeconds, output.lyricsUsed)
            }
            // Ask the server to delete its copy now that we have the audio.
            runCatching { http.send(request("/v1/generations/$jobId").delete().build()).close() }
            return results
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(NonCancellable) {
                runCatching { http.send(request("/v1/generations/$jobId/cancel").post(ByteArray(0).toRequestBody()).build()).close() }
            }
            throw e
        }
    }

    companion object {
        const val ID = "backend"
        private val JSON = "application/json".toMediaType()
    }
}
