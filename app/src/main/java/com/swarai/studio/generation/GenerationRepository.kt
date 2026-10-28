package com.swarai.studio.generation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.swarai.studio.data.db.AppDatabase
import com.swarai.studio.data.db.GenerationJobEntity
import com.swarai.studio.engine.EngineInfo
import com.swarai.studio.engine.EngineKind
import com.swarai.studio.engine.GenerationSpec
import com.swarai.studio.engine.SwarJson
import kotlinx.coroutines.flow.Flow

class GenerationRepository(private val context: Context, private val db: AppDatabase) {
    fun observe(jobId: Long): Flow<GenerationJobEntity?> = db.jobs().observe(jobId)
    fun recent(): Flow<List<GenerationJobEntity>> = db.jobs().observeRecent()

    suspend fun enqueue(projectId: Long?, engine: EngineInfo, spec: GenerationSpec, voicePreviewId: String? = null): Long {
        val jobId = db.jobs().insert(
            GenerationJobEntity(
                projectId = projectId,
                engineId = engine.id,
                engineName = engine.name,
                specJson = SwarJson.encodeToString(GenerationSpec.serializer(), spec),
                estimatedCostUsd = engine.estimateUsd(spec),
                voicePreviewId = voicePreviewId,
            )
        )
        // A companion on the local network must work without internet, so it gets no network constraint.
        val network = if (engine.kind == EngineKind.COMPANION) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setInputData(workDataOf(GenerationWorker.KEY_JOB_ID to jobId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(jobId), ExistingWorkPolicy.KEEP, request)
        return jobId
    }

    suspend fun cancel(jobId: Long) {
        // Mark first: the worker treats a stop as "cancelled" only when the user asked for it (see GenerationWorker).
        db.jobs().get(jobId)?.takeIf { it.status == "QUEUED" || it.status == "RUNNING" }?.let {
            db.jobs().update(it.copy(status = "CANCELLED", finishedAt = System.currentTimeMillis(), progress = "Cancelled"))
        }
        WorkManager.getInstance(context).cancelUniqueWork(workName(jobId))
    }

    companion object {
        const val TAG = "generation"
        fun workName(jobId: Long) = "generation-$jobId"
    }
}
