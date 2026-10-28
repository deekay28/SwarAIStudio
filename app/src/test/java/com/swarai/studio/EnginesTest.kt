package com.swarai.studio

import com.swarai.studio.engine.AceQueryItem
import com.swarai.studio.engine.AceResultItem
import com.swarai.studio.engine.AceStepCompanionEngine
import com.swarai.studio.engine.BackendEngine
import com.swarai.studio.engine.DeepInfraDirectEngine
import com.swarai.studio.engine.EngineException
import com.swarai.studio.engine.GenerationCallbacks
import com.swarai.studio.engine.GenerationSpec
import com.swarai.studio.engine.SwarJson
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnginesTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private val fakeMp3 = byteArrayOf(0x49, 0x44, 0x33) + ByteArray(4000) { 1 }
    private val outDir: File = Files.createTempDirectory("swarai").toFile()
    private val spec = GenerationSpec(caption = "bhojpuri folk, female vocal", lyrics = "[verse]\nजय छठी मईया", durationSec = 60, variations = 2)
    private val submitted = mutableListOf<String>()
    private val callbacks = object : GenerationCallbacks {
        override suspend fun onSubmitted(resumeToken: String) { submitted += resumeToken }
        override suspend fun onProgress(message: String) {}
    }

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown(); outDir.deleteRecursively() }

    private fun dispatch(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = handler(request) }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun backendFlowSubmitsPollsDownloadsAndDeletes() = runBlocking {
        val seen = mutableListOf<String>()
        var polls = 0
        dispatch { r ->
            seen += "${r.method} ${r.path}"
            assertEquals("Bearer tok", r.getHeader("Authorization"))
            when {
                r.path == "/v1/providers" -> json("""[{"id":"deepinfra-acestep15","name":"ACE-Step 1.5 XL","model":"m","hosting":"hosted","sends_data_to":"DeepInfra","pricing":{"usd_per_unit":0.001},"capabilities":{"min_duration_seconds":30,"max_duration_seconds":500,"formats":["mp3"]}}]""")
                r.method == "POST" && r.path == "/v1/generations" -> {
                    val body = r.body.readUtf8()
                    assertTrue(body.contains("जय छठी मईया"))
                    assertTrue(body.contains("\"variations\":2"))
                    json("""{"id":"j1","provider":"p","status":"queued","outputs":[]}""")
                }
                r.path == "/v1/generations/j1" && r.method == "GET" -> {
                    polls++
                    if (polls < 2) json("""{"id":"j1","provider":"p","status":"running","outputs":[{"index":0,"status":"running"},{"index":1,"status":"running"}]}""")
                    else json("""{"id":"j1","provider":"p","status":"succeeded","outputs":[{"index":0,"status":"succeeded","audio_path":"/v1/generations/j1/outputs/0","format":"mp3","seed":5},{"index":1,"status":"succeeded","audio_path":"/v1/generations/j1/outputs/1","format":"mp3","seed":6}]}""")
                }
                r.path!!.startsWith("/v1/generations/j1/outputs/") -> MockResponse().setBody(Buffer().write(fakeMp3))
                r.method == "DELETE" -> MockResponse().setResponseCode(204)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val engine = BackendEngine(server.url("/").toString(), "tok", null, http, pollIntervalMs = 1)
        val info = engine.describe()
        assertTrue(info.available)
        assertEquals(60, info.clampDuration(60))
        val results = engine.generate(spec, outDir, null, callbacks)
        assertEquals(2, results.size)
        assertArrayEquals(fakeMp3, results[1].file.readBytes())
        assertEquals(listOf("j1"), submitted)
        assertTrue(seen.contains("DELETE /v1/generations/j1"))
    }

    @Test fun backendResumesWithoutResubmitting() = runBlocking {
        dispatch { r ->
            when {
                r.path == "/v1/providers" -> json("""[{"id":"p","name":"P","model":"m","hosting":"hosted","sends_data_to":"x"}]""")
                r.method == "POST" -> error("must not resubmit")
                r.path == "/v1/generations/old" -> json("""{"id":"old","provider":"p","status":"succeeded","outputs":[{"index":0,"status":"succeeded","audio_path":"/v1/generations/old/outputs/0","format":"mp3"}]}""")
                r.path == "/v1/generations/old/outputs/0" -> MockResponse().setBody(Buffer().write(fakeMp3))
                else -> MockResponse().setResponseCode(204)
            }
        }
        val engine = BackendEngine(server.url("/").toString(), "tok", null, http, pollIntervalMs = 1)
        assertEquals(1, engine.generate(spec, outDir, "old", callbacks).size)
        assertTrue(submitted.isEmpty())
    }

    @Test fun backendReportsServerErrorsReadably() = runBlocking {
        dispatch { MockResponse().setResponseCode(401).setBody("""{"detail":"Invalid token."}""") }
        val engine = BackendEngine(server.url("/").toString(), "bad", null, http)
        val info = engine.describe()
        assertTrue(!info.available && info.unavailableReason!!.contains("credentials"))
    }

    @Test fun deepInfraDecodesDataUriAudio() = runBlocking {
        val b64 = Base64.getEncoder().encodeToString(fakeMp3)
        var calls = 0
        dispatch { r ->
            calls++
            assertEquals("Bearer dk", r.getHeader("Authorization"))
            assertTrue(r.path!!.endsWith("/ACE-Step/acestep-v15-xl-sft"))
            val body = r.body.readUtf8()
            assertTrue(body.contains("\"response_format\":\"mp3\""))
            json("""{"audio":"data:audio/mpeg;base64,$b64","seed":${41 + calls},"duration_seconds":60.0}""")
        }
        val engine = DeepInfraDirectEngine("dk", http = http, baseUrl = server.url("/v1/inference/").toString())
        val results = engine.generate(spec, outDir, null, callbacks)
        assertEquals(2, results.size)
        assertEquals("mp3", results[0].format)
        assertArrayEquals(fakeMp3, results[0].file.readBytes())
    }

    @Test fun deepInfraRejectsNonAudio() {
        val b64 = Base64.getEncoder().encodeToString("{\"error\":1}".toByteArray())
        dispatch { json("""{"audio":"$b64"}""") }
        val engine = DeepInfraDirectEngine("dk", http = http, baseUrl = server.url("/").toString())
        assertThrows(EngineException::class.java) { runBlocking { engine.generate(spec.copy(variations = 1), outDir, null, callbacks) } }
    }

    @Test fun deepInfraKeyErrors() {
        dispatch { MockResponse().setResponseCode(402).setBody("{}") }
        val engine = DeepInfraDirectEngine("dk", http = http, baseUrl = server.url("/").toString())
        val error = assertThrows(EngineException::class.java) { runBlocking { engine.generate(spec, outDir, null, callbacks) } }
        assertTrue(error.message!!.contains("credit"))
    }

    @Test fun aceStepCompanionFlow() = runBlocking {
        dispatch { r ->
            when (r.path) {
                "/release_task" -> {
                    val body = r.body.readUtf8()
                    assertTrue(body.contains("\"vocal_language\":\"hi\"") && body.contains("\"batch_size\":2"))
                    json("""{"data":{"task_id":"t1","status":"queued"},"code":200}""")
                }
                "/query_result" -> {
                    val result = JsonPrimitive("""[{"file":"/v1/audio?path=%2Ftmp%2Fa.mp3","status":1,"seed_value":"7"},{"file":"/v1/audio?path=%2Ftmp%2Fb.mp3","status":1}]""")
                    json("""{"data":[{"task_id":"t1","status":1,"result":$result}],"code":200}""")
                }
                else -> if (r.path!!.startsWith("/v1/audio")) MockResponse().setBody(Buffer().write(fakeMp3)) else MockResponse().setResponseCode(404)
            }
        }
        val engine = AceStepCompanionEngine(server.url("/").toString(), "", http, pollIntervalMs = 1)
        val results = engine.generate(spec, outDir, null, callbacks)
        assertEquals(2, results.size)
        assertEquals(7L, results[0].seed)
        assertEquals(listOf("t1"), submitted)
    }

    @Test fun aceStepSkipsThinkingWhenServerHasNoLm() = runBlocking {
        var thinkingSent: String? = null
        dispatch { r ->
            when (r.path) {
                "/health" -> json("""{"data":{"status":"ok","llm_initialized":false,"loaded_model":"acestep-v15-turbo"},"code":200}""")
                "/release_task" -> {
                    thinkingSent = Regex("\"thinking\":(true|false)").find(r.body.readUtf8())?.groupValues?.get(1)
                    json("""{"data":{"task_id":"t2"},"code":200}""")
                }
                "/query_result" -> json("""{"data":[{"task_id":"t2","status":1,"result":${JsonPrimitive("""[{"file":"/v1/audio?path=x.mp3","status":1}]""")}}],"code":200}""")
                else -> MockResponse().setBody(Buffer().write(fakeMp3))
            }
        }
        val engine = AceStepCompanionEngine(server.url("/").toString(), "pw", http, pollIntervalMs = 1)
        assertTrue(engine.testConnection().contains("not loaded"))
        engine.generate(spec.copy(variations = 1), outDir, null, callbacks)
        assertEquals("false", thinkingSent)
    }

    @Test fun aceStepRetriesTunnelHiccupsWhilePolling() = runBlocking {
        var queries = 0
        dispatch { r ->
            when (r.path) {
                "/release_task" -> json("""{"data":{"task_id":"t3"},"code":200}""")
                "/query_result" -> {
                    queries++
                    if (queries <= 2) MockResponse().setResponseCode(530) // Cloudflare: origin briefly unreachable
                    else json("""{"data":[{"task_id":"t3","status":1,"result":${JsonPrimitive("""[{"file":"/v1/audio?path=x.mp3"}]""")}}],"code":200}""")
                }
                else -> if (r.path!!.startsWith("/v1/audio")) MockResponse().setBody(Buffer().write(fakeMp3)) else MockResponse().setResponseCode(404)
            }
        }
        val engine = AceStepCompanionEngine(server.url("/").toString(), "", http, pollIntervalMs = 1)
        assertEquals(1, engine.generate(spec.copy(variations = 1), outDir, null, callbacks).size)
        assertEquals(3, queries)
    }

    @Test fun aceStepGivesUpAfterRepeatedFailures() {
        dispatch { r ->
            when (r.path) {
                "/release_task" -> json("""{"data":{"task_id":"t4"},"code":200}""")
                else -> MockResponse().setResponseCode(530)
            }
        }
        val engine = AceStepCompanionEngine(server.url("/").toString(), "", http, pollIntervalMs = 1)
        val error = assertThrows(EngineException::class.java) { runBlocking { engine.generate(spec, outDir, null, callbacks) } }
        assertTrue(error.message!!.contains("530"))
    }

    @Test fun aceStepFailureShowsServerReason() {
        val traceback = "Traceback (most recent call last):\n  File \"x.py\", line 1\ntorch.OutOfMemoryError: CUDA out of memory. Tried to allocate 2.00 GiB"
        val result = SwarJson.encodeToString(ListSerializer(AceResultItem.serializer()), listOf(AceResultItem(status = 2, error = traceback)))
        val message = AceStepCompanionEngine.failureMessage(AceQueryItem("t", 2, JsonPrimitive(result)))
        assertTrue(message, message.contains("torch.OutOfMemoryError: CUDA out of memory"))
        assertTrue(message, message.contains("shorter song"))
        assertEquals("ACE-Step server reported that generation failed.", AceStepCompanionEngine.failureMessage(AceQueryItem("t", 2)))
        val nan = SwarJson.encodeToString(ListSerializer(AceResultItem.serializer()),
            listOf(AceResultItem(status = 2, error = "RuntimeError: Generation produced NaN or Inf latents (dtype=torch.float16)")))
        assertTrue(AceStepCompanionEngine.failureMessage(AceQueryItem("t", 2, JsonPrimitive(nan))).contains("float32"))
    }

    @Test fun aceStepResultParsingVariants() {
        assertEquals(1, AceStepCompanionEngine.parseResults(JsonPrimitive("""{"file":"/a"}""")).size)
        assertEquals(0, AceStepCompanionEngine.parseResults(JsonPrimitive("garbage")).size)
        assertEquals(0, AceStepCompanionEngine.parseResults(null).size)
    }
}

class CompanionLabellingTest {
    @Test fun colabTunnelIsLabelledOnline() = runBlocking {
        val info = AceStepCompanionEngine("https://blue-sky-123.trycloudflare.com", "pw").describe()
        assertEquals(com.swarai.studio.engine.EngineKind.CLOUD_NOTEBOOK, info.kind)
        assertTrue(!info.worksWithoutInternet)
        assertTrue(info.sendsDataTo.contains("Colab"))
        assertTrue(info.pricing.isFree)
    }

    @Test fun lanServerIsLabelledLocal() = runBlocking {
        val info = AceStepCompanionEngine("http://192.168.1.20:8001", "").describe()
        assertEquals(com.swarai.studio.engine.EngineKind.COMPANION, info.kind)
        assertTrue(info.worksWithoutInternet)
    }
}
