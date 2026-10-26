package com.swarai.studio.generation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swarai.studio.SwarApp
import com.swarai.studio.audio.AudioCodec
import com.swarai.studio.data.db.GenerationJobEntity
import com.swarai.studio.data.db.SongEntity
import com.swarai.studio.engine.EngineException
import com.swarai.studio.engine.GenerationCallbacks
import com.swarai.studio.engine.GenerationSpec
import com.swarai.studio.engine.SwarJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs one generation job. Never retries automatically: a retry could bill the user twice. If the process dies
 * mid-job, resumable engines continue polling the existing remote job; others are marked as interrupted.
 */
class GenerationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SwarApp).container
        val jobs = container.db.jobs()
        val jobId = inputData.getLong(KEY_JOB_ID, -1)
        var job = jobs.get(jobId) ?: return Result.failure()
        if (job.status in TERMINAL) return Result.success()
        val engine = container.engines.engine(job.engineId)
        if (job.remoteJobId != null && !engine.resumable) {
            jobs.update(job.copy(status = "FAILED", finishedAt = now(),
                error = "The app was closed while this song was generating. It was not resubmitted to avoid a second charge."))
            return Result.failure()
        }
        val spec = SwarJson.decodeFromString(GenerationSpec.serializer(), job.specJson)
        job = job.copy(status = "RUNNING", progress = "Submitting…")
        jobs.update(job)
        val outputDir = container.songsDir
        return try {
            val info = engine.describe()
            val results = engine.generate(spec, outputDir, job.remoteJobId, object : GenerationCallbacks {
                override suspend fun onSubmitted(resumeToken: String) {
                    job = job.copy(remoteJobId = resumeToken, progress = "Submitted")
                    jobs.update(job)
                }

                override suspend fun onProgress(message: String) {
                    job = job.copy(progress = message)
                    jobs.update(job)
                }
            })
            if (results.isEmpty()) throw EngineException("The engine returned no audio.")
            val project = job.projectId?.let { container.db.projects().get(it) }
            val baseTitle = job.voicePreviewId?.let { "Voice preview · ${com.swarai.studio.catalog.Catalog.voice(it).name}" }
                ?: project?.title?.ifBlank { null } ?: "Untitled song"
            results.forEachIndexed { index, audio ->
                val durationMs = AudioCodec.durationMs(audio.file).takeIf { it > 0 } ?: ((audio.durationSec ?: 0.0) * 1000).toLong()
                container.db.songs().insert(
                    SongEntity(
                        projectId = job.projectId,
                        title = if (results.size > 1) "$baseTitle (take ${index + 1})" else baseTitle,
                        filePath = audio.file.absolutePath,
                        format = audio.format,
                        durationMs = durationMs,
                        sourceKind = "generated",
                        engineId = info.id,
                        providerName = info.name,
                        isAiGenerated = info.isAiMusic,
                        seed = audio.seed,
                        caption = spec.caption,
                        lyricsUsed = audio.lyricsUsed ?: spec.lyrics,
                        voicePreviewId = job.voicePreviewId,
                    )
                )
            }
            jobs.update(job.copy(status = "SUCCEEDED", progress = "${results.size} song(s) ready", finishedAt = now()))
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { markEnded(job, "CANCELLED", "Cancelled") }
            throw e
        } catch (e: EngineException) {
            markEnded(job, "FAILED", e.message)
            Result.failure()
        } catch (e: IOException) {
            markEnded(job, "FAILED", "Network problem: ${e.message ?: e.javaClass.simpleName}. Nothing was resubmitted.")
            Result.failure()
        } catch (e: kotlinx.serialization.SerializationException) {
            markEnded(job, "FAILED", "The engine sent a response this app doesn't understand.")
            Result.failure()
        } catch (e: Exception) {
            markEnded(job, "FAILED", "Unexpected error: ${e.message ?: e.javaClass.simpleName}")
            Result.failure()
        }
    }

    private suspend fun markEnded(job: GenerationJobEntity, status: String, message: String?) {
        val jobs = (applicationContext as SwarApp).container.db.jobs()
        val latest = jobs.get(job.id) ?: job
        jobs.update(latest.copy(status = status, error = message, progress = status.lowercase(), finishedAt = now()))
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_JOB_ID = "job_id"
        val TERMINAL = setOf("SUCCEEDED", "FAILED", "CANCELLED")
    }
}
