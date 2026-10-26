package com.swarai.studio.engine

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DeepInfraRequest(
    val prompt: String,
    val lyrics: String,
    val instrumental: Boolean,
    val duration: Int,
    @SerialName("response_format") val responseFormat: String,
    @SerialName("negative_prompt") val negativePrompt: String? = null,
    val seed: Long? = null,
)

@Serializable
data class DeepInfraResponse(
    val audio: JsonElement? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    val seed: Long? = null,
    @SerialName("generated_lyrics") val generatedLyrics: String? = null,
)

/**
 * Calls DeepInfra's hosted ACE-Step 1.5 XL directly with a key the user typed into Settings.
 * The key is stored encrypted on this phone only. Use the SwarAI server mode to keep keys off the phone.
 */
class DeepInfraDirectEngine(
    private val apiKey: String,
    private val model: String = MODEL,
    private val http: OkHttpClient = Http.client,
    private val baseUrl: String = "https://api.deepinfra.com/v1/inference/",
) : MusicEngine {
    override val id = ID
    override val resumable = false

    override suspend fun describe() = EngineInfo(
        id = ID,
        name = "ACE-Step 1.5 XL on DeepInfra (your key)",
        kind = EngineKind.HOSTED_DIRECT,
        model = model,
        sendsDataTo = "DeepInfra, Inc. (USA) at api.deepinfra.com",
        available = apiKey.isNotBlank(),
        unavailableReason = if (apiKey.isBlank()) "Add your DeepInfra API key in Settings." else null,
        license = "ACE-Step 1.5: MIT. Subject to DeepInfra's terms of service.",
        pricing = EnginePricing(USD_PER_SECOND, "DeepInfra bills your account per second of generated audio (about \$0.18 for 3 minutes)."),
        capabilities = EngineCapabilities(
            minDurationSec = 30, maxDurationSec = 500, maxVariations = 4,
            formats = listOf("mp3", "wav", "flac"), cancel = "abandon_only",
            languagesNote = "50+ languages incl. Hindi. Bhojpuri/Maithili are sung with Hindi pronunciation.",
        ),
    )

    override suspend fun testConnection(): String {
        if (apiKey.isBlank()) throw EngineException("No DeepInfra key saved.")
        // A free request (no audio is generated). Only an explicit 401/403 proves the key is wrong.
        val response = http.send(
            Request.Builder().url("https://api.deepinfra.com/v1/me").header("Authorization", "Bearer $apiKey").get().build()
        )
        val code = response.code
        response.close()
        return when (code) {
            in 200..299 -> "Key accepted by DeepInfra."
            401, 403 -> throw httpError("DeepInfra", code)
            else -> "Reached DeepInfra, but could not verify the key (HTTP $code). Generation may still work."
        }
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
        val baseSeed = spec.seed ?: (System.nanoTime() and 0x7FFFFFFF)
        val count = spec.variations.coerceIn(1, info.capabilities.maxVariations)
        callbacks.onSubmitted("direct")
        return (0 until count).map { index ->
            callbacks.onProgress("Generating ${index + 1} of $count on DeepInfra…")
            val body = DeepInfraRequest(
                prompt = listOfNotNull(spec.caption, spec.bpm?.let { "$it BPM" }, spec.keyScale).joinToString(", "),
                lyrics = if (spec.instrumental) "" else spec.lyrics,
                instrumental = spec.instrumental,
                duration = info.clampDuration(spec.durationSec),
                responseFormat = format,
                negativePrompt = spec.negativePrompt,
                seed = baseSeed + index,
            )
            val response = http.send(
                Request.Builder().url(baseUrl + model)
                    .header("Authorization", "Bearer $apiKey")
                    .post(SwarJson.encodeToString(DeepInfraRequest.serializer(), body).toRequestBody(JSON))
                    .build()
            )
            val code = response.code
            val text = response.bodyString()
            if (code !in 200..299) throw httpError("DeepInfra", code, text)
            val parsed = SwarJson.decodeFromString(DeepInfraResponse.serializer(), text)
            val bytes = decodeAudio(parsed.audio)
            val actual = sniffAudioFormat(bytes) ?: throw EngineException("DeepInfra returned data that is not audio.")
            val file = File(outputDir, "deepinfra-${System.currentTimeMillis()}-$index.$actual")
            withContext(Dispatchers.IO) { file.parentFile?.mkdirs(); file.writeBytes(bytes) }
            GeneratedAudio(file, actual, parsed.seed ?: body.seed, parsed.durationSeconds, parsed.generatedLyrics)
        }
    }

    private suspend fun decodeAudio(element: JsonElement?): ByteArray {
        val value = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> (element["url"] as? JsonPrimitive)?.contentOrNull ?: (element["data"] as? JsonPrimitive)?.contentOrNull
            else -> null
        } ?: throw EngineException("DeepInfra returned no audio.")
        if (value.startsWith("https://")) {
            val response = http.send(Request.Builder().url(value).get().build())
            if (!response.isSuccessful) throw httpError("DeepInfra download", response.code).also { response.close() }
            return withContext(Dispatchers.IO) { response.use { it.body!!.bytes() } }
        }
        val b64 = if (value.startsWith("data:")) value.substringAfter(',') else value
        return runCatching { java.util.Base64.getMimeDecoder().decode(b64) }.getOrElse {
            throw EngineException("DeepInfra returned audio in an unknown encoding.")
        }
    }

    companion object {
        const val ID = "deepinfra_direct"
        const val MODEL = "ACE-Step/acestep-v15-xl-sft"
        const val USD_PER_SECOND = 0.001
        private val JSON = "application/json".toMediaType()
    }
}
