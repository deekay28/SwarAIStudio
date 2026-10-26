package com.swarai.studio.engine

import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

val SwarJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Remote requests must use HTTPS. Plain HTTP is only allowed to addresses on your own network
 * (private IPv4 ranges, Tailscale's 100.64/10, localhost, *.local), which is how a computer companion is reached.
 */
object NetworkPolicy {
    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h == "::1") return true
        val parts = h.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || h.count { it == '.' } != 3) return false
        val (a, b) = parts
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
            (a == 169 && b == 254) || (a == 100 && b in 64..127)
    }

    /** Returns a normalised base URL or throws with a user-readable reason. */
    fun validateBaseUrl(raw: String, allowLocalHttp: Boolean): String {
        val url = raw.trim().trimEnd('/')
        val uri = runCatching { URI(url) }.getOrNull() ?: throw EngineException("That address is not a valid URL.")
        val host = uri.host ?: throw EngineException("The address must include a host name.")
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> if (!allowLocalHttp || !isLocalHost(host)) {
                throw EngineException(
                    if (allowLocalHttp) "Plain http:// is only allowed for addresses on your own network. Use https://."
                    else "This connection must use https://."
                )
            }
            else -> throw EngineException("The address must start with https://")
        }
        return url
    }

    val interceptor = Interceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps && !isLocalHost(url.host)) throw IOException("Blocked insecure request to ${url.host}")
        chain.proceed(chain.request())
    }
}

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Hosted inference can hold the connection open while a full song renders.
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .addInterceptor(NetworkPolicy.interceptor)
            .build()
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

suspend fun OkHttpClient.send(request: Request): Response = newCall(request).await()

suspend fun Response.bodyString(): String = withContext(Dispatchers.IO) { use { it.body?.string().orEmpty() } }

suspend fun Response.saveTo(file: File): Long = withContext(Dispatchers.IO) {
    use { response ->
        val body = response.body ?: throw EngineException("Empty download")
        file.parentFile?.mkdirs()
        file.outputStream().use { out -> body.byteStream().copyTo(out) }
    }
}

fun httpError(provider: String, code: Int, detail: String = ""): EngineException = EngineException(
    when (code) {
        401, 403 -> "$provider rejected the credentials (HTTP $code). Check them in Settings."
        402 -> "$provider reports insufficient credit or billing not set up."
        404 -> "$provider: not found (HTTP 404). ${detail.take(200)}"
        422, 400 -> "$provider rejected the request: ${detail.take(300)}"
        429 -> "$provider is busy or a limit was reached. ${detail.take(200)}"
        else -> "$provider failed (HTTP $code). ${detail.take(200)}"
    }.trim()
)

/** Magic-byte check so a JSON error page is never saved as a "song". */
fun sniffAudioFormat(bytes: ByteArray): String? {
    fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
    return when {
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE" -> "wav"
        bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "fLaC" -> "flac"
        bytes.size >= 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "ID3" -> "mp3"
        at(0) == 0xFF && (at(1) and 0xE0) == 0xE0 -> "mp3"
        bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "OggS" -> "ogg"
        bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "m4a"
        else -> null
    }
}
