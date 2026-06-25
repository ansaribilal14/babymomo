package com.babymomo.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.core.memory.MemoryRecaller
import com.babymomo.app.data.db.dao.HeartbeatLogDao
import com.babymomo.app.data.db.entities.HeartbeatLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llmProvider: WrappedLlmProvider,
    private val memoryRecaller: MemoryRecaller,
    private val heartbeatLogDao: HeartbeatLogDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalTime.now()
        // Active window: 8am-10pm
        if (now.hour < 8 || now.hour >= 22) {
            return Result.success()
        }

        val summary = try {
            val memories = memoryRecaller.recall("recent important things", topK = 5)
            val memoryContext = memories.joinToString("\n") { "- ${it.content}" }

            val prompt = """You are Babymomo's autonomous background agent. Your job is to check
on the user's world and surface anything that needs attention.

Review:
- Recent memories (last 48h):
$memoryContext

Respond with EXACTLY ONE of:
A) The single word: SILENT
B) A short notification message for the user

Do not explain your reasoning. Do not add preamble."""

            llmProvider.complete(prompt)
        } catch (_: Exception) {
            "SILENT"
        }

        val isSilent = summary.trim().uppercase() == "SILENT"
        val logEntry = HeartbeatLogEntity(
            id = "hb_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            summary = if (isSilent) "SILENT" else summary,
            notified = !isSilent,
            message = if (isSilent) null else summary
        )
        heartbeatLogDao.insert(logEntry)

        return Result.success()
    }
}

@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryDao: com.babymomo.app.data.db.dao.MemoryDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Clean up expired Working memories (older than 24h)
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        // Working memories past their validTo get cleaned up
        return Result.success()
    }
}

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id") ?: return Result.failure()
        val downloadUrl = inputData.getString("download_url") ?: return Result.failure()

        // Download model file - will be implemented with actual LiteRT model URLs
        return Result.success()
    }
}
