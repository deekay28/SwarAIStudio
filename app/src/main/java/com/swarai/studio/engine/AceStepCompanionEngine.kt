package com.swarai.studio.engine

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class AceReleaseRequest(
    val prompt: String,
    val lyrics: String,
    @SerialName("vocal_language") val vocalLanguage: String,
    @SerialName("audio_duration") val audioDuration: Int,
    @SerialName("audio_format") val audioFormat: String,
    @SerialName("batch_size") val batchSize: Int,
    val thinking: Boolean = true,
    val bpm: Int? = null,
    @SerialName("key_scale") val keyScale: String? = null,
    @SerialName("use_random_seed") val useRandomSeed: Boolean = true,
    val seed: Long? = null,
)

@Serializable
data class AceEnvelope<T>(val data: T? = null, val code: Int = 200, val error: String? = null)

@Serializable
data class AceTask(@SerialName("task_id") val taskId: String)

@Serializable
data class AceQueryItem(@SerialName("task_id") val taskId: String, val status: Int, val result: JsonElement? = null)

@Serializable
data class AceResultItem(
    val file: String = "",
    val status: Int = 0,
    val lyrics: String? = null,
    @SerialName("seed_value") val seedValue: String? = null,
    val metas: AceMetas? = null,
)

@Serializable
data class AceMetas(val duration: Double? = null, val bpm: Double? = null)

@Serializable
private data class AceQueryRequest(@SerialName("task_id_list") val taskIdList: List<String>)

/**
 * Connects to the official ACE-Step 1.5 REST API server (`uv run acestep-api`) running on the user's own
 * computer. Lyrics never leave the local network, and generation works without an internet connection.
 */
class AceStepCompanionEngine(
    private val baseUrlRaw: String,
    private val apiKey: String,
    private val http: OkHttpClient = Http.client,
    private val pollIntervalMs: Long = 2000,
) : MusicEngine {
    override val id = ID
    override val resumable = true

    private val baseUrl get() = NetworkPolicy.validateBaseUrl(baseUrlRaw, allowLocalHttp = true)

    private fun request(path: String) = Request.Builder().url(baseUrl + path).apply {
        if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
    }

    /** A public https address (e.g. the free Colab notebook's Cloudflare tunnel) means generation happens online. */
    val isRemote: Boolean
        get() = runCatching { java.net.URI(baseUrlRaw.trim()).host }.getOrNull()?.let { !NetworkPolicy.isLocalHost(it) } ?: false

    override suspend fun describe(): EngineInfo {
        val host = runCatching { java.net.URI(baseUrlRaw.trim()).host }.getOrNull() ?: baseUrlRaw
        return EngineInfo(
            id = ID,
            name = if (isRemote) "ACE-Step 1.5 on your cloud notebook" else "ACE-Step 1.5 on your computer",
            kind = if (isRemote) EngineKind.CLOUD_NOTEBOOK else EngineKind.COMPANION,
            model = "ACE-Step 1.5 (model chosen on the server)",
            sendsDataTo = when {
                baseUrlRaw.isBlank() -> "Your own ACE-Step server"
                isRemote && host.endsWith("trycloudflare.com") ->
                    "Your Colab notebook (Google's servers) through a Cloudflare tunnel at $host"
                isRemote -> "Your own ACE-Step server at $host (over the internet)"
                else -> "Your own computer at $baseUrlRaw"
            },
            available = baseUrlRaw.isNotBlank(),
            unavailableReason = if (baseUrlRaw.isBlank()) "Set your ACE-Step server address in Settings (your PC or the free Colab notebook)." else null,
            license = "ACE-Step 1.5: MIT.",
            pricing = EnginePricing(
                0.0,
                if (isRemote) "No per-song fee. Free Colab GPU time is limited by Google." else "No per-song fee. Runs on your own GPU/CPU.",
            ),
            capabilities = EngineCapabilities(
                minDurationSec = 10, maxDurationSec = 600, maxVariations = 4,
                formats = listOf("mp3", "wav", "flac"), cancel = "abandon_only",
                languagesNote = "50+ languages incl. Hindi (Devanagari).",
            ),
        )
    }

    override suspend fun testConnection(): String {
        val response = http.send(request("/health").get().build())
        val code = response.code
        val body = response.bodyString()
        if (code !in 200..299) throw httpError("ACE-Step server", code, body)
        return "Connected to ACE-Step server."
    }

    override suspend fun generate(
        spec: GenerationSpec,
        outputDir: File,
        resumeToken: String?,
        callbacks: GenerationCallbacks,
    ): List<GeneratedAudio> {
        val info = describe()
        if (!info.available) throw EngineException(info.unavailableReason!!)
        val format = if (spec.format in info.capabilities.formats) spec.format else "mp3"
        val taskId = resumeToken ?: run {
            val body = AceReleaseRequest(
                prompt = spec.caption,
                lyrics = if (spec.instrumental) "[Instrumental]" else spec.lyrics,
                vocalLanguage = spec.languageCode,
                audioDuration = info.clampDuration(spec.durationSec),
                audioFormat = format,
                batchSize = spec.variations.coerceIn(1, 4),
                bpm = spec.bpm,
                keyScale = spec.keyScale,
                useRandomSeed = spec.seed == null,
                seed = spec.seed,
            )
            val response = http.send(
                request("/release_task").post(SwarJson.encodeToString(AceReleaseRequest.serializer(), body).toRequestBody(JSON)).build()
            )
            val code = response.code
            val text = response.bodyString()
            if (code !in 200..299) throw httpError("ACE-Step server", code, text)
            val task = SwarJson.decodeFromString(AceEnvelope.serializer(AceTask.serializer()), text).data
                ?: throw EngineException("ACE-Step server did not return a task id.")
            callbacks.onSubmitted(task.taskId)
            task.taskId
        }
        var item = AceQueryItem(taskId, 0)
        while (true) {
            val query = SwarJson.encodeToString(AceQueryRequest.serializer(), AceQueryRequest(listOf(taskId)))
            val response = http.send(request("/query_result").post(query.toRequestBody(JSON)).build())
            val code = response.code
            val text = response.bodyString()
            if (code !in 200..299) throw httpError("ACE-Step server", code, text)
            val items = SwarJson.decodeFromString(AceEnvelope.serializer(ListSerializer(AceQueryItem.serializer())), text).data.orEmpty()
            item = items.firstOrNull { it.taskId == taskId } ?: AceQueryItem(taskId, 0)
            if (item.status == 1) break
            if (item.status == 2) throw EngineException("ACE-Step server reported that generation failed.")
            callbacks.onProgress("Rendering on your computer…")
            delay(pollIntervalMs)
        }
        val results = parseResults(item.result).filter { it.file.isNotBlank() }
        if (results.isEmpty()) throw EngineException("ACE-Step server returned no audio files.")
        return results.mapIndexed { index, result ->
            val path = if (result.file.startsWith("/")) result.file else "/${result.file}"
            val response = http.send(request(path).get().build())
            if (!response.isSuccessful) throw httpError("ACE-Step server", response.code).also { response.close() }
            val file = File(outputDir, "acestep-$taskId-$index.$format")
            response.saveTo(file)
            val actual = sniffAudioFormat(ByteArray(16).also { head -> file.inputStream().use { it.read(head) } }) ?: format
            val finalFile = if (actual != format) File(outputDir, "acestep-$taskId-$index.$actual").also { file.renameTo(it) } else file
            GeneratedAudio(
                finalFile, actual,
                result.seedValue?.split(',')?.firstOrNull()?.trim()?.toLongOrNull(),
                result.metas?.duration, result.lyrics,
            )
        }
    }

    companion object {
        const val ID = "companion"
        private val JSON = "application/json".toMediaType()

        /** The API returns `result` as a JSON string containing a list (or a single object). */
        fun parseResults(element: JsonElement?): List<AceResultItem> {
            val list = ListSerializer(AceResultItem.serializer())
            return runCatching {
                when (element) {
                    is JsonPrimitive -> {
                        val text = element.contentOrNull ?: return emptyList()
                        if (text.trimStart().startsWith("[")) SwarJson.decodeFromString(list, text)
                        else listOf(SwarJson.decodeFromString(AceResultItem.serializer(), text))
                    }
                    is JsonArray -> SwarJson.decodeFromJsonElement(list, element)
                    null -> emptyList()
                    else -> listOf(SwarJson.decodeFromJsonElement(AceResultItem.serializer(), element))
                }
            }.getOrDefault(emptyList())
        }
    }
}
