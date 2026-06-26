package com.babymomo.app.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.babymomo.app.core.llm.LocalLlmProvider
import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.core.memory.MemoryRecaller
import com.babymomo.app.data.db.dao.HeartbeatLogDao
import com.babymomo.app.data.db.dao.ModelCatalogDao
import com.babymomo.app.data.db.entities.HeartbeatLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime

// ─── Heartbeat Worker ───

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
        if (now.hour < 8 || now.hour >= 22) return Result.success()

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
        } catch (_: Exception) { "SILENT" }

        val isSilent = summary.trim().uppercase() == "SILENT"
        heartbeatLogDao.insert(HeartbeatLogEntity(
            id = "hb_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            summary = if (isSilent) "SILENT" else summary,
            notified = !isSilent,
            message = if (isSilent) null else summary
        ))
        return Result.success()
    }
}

// ─── Memory Maintenance Worker ───

@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryDao: com.babymomo.app.data.db.dao.MemoryDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}

// ─── Model Download Worker ───

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelCatalogDao: ModelCatalogDao,
    private val localLlmProvider: LocalLlmProvider
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id") ?: return Result.failure()
        val downloadUrl = inputData.getString("download_url") ?: return Result.failure()
        val filename = inputData.getString("filename") ?: return Result.failure()

        return try {
            val modelsDir = File(applicationContext.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val targetFile = File(modelsDir, filename)

            if (targetFile.exists() && targetFile.length() > 0) {
                modelCatalogDao.markDownloaded(modelId, System.currentTimeMillis())
                activateModel(modelId, targetFile.absolutePath)
                return Result.success()
            }

            withContext(Dispatchers.IO) { downloadFile(downloadUrl, targetFile, modelId) }

            if (targetFile.exists() && targetFile.length() > 0) {
                modelCatalogDao.markDownloaded(modelId, System.currentTimeMillis())
                activateModel(modelId, targetFile.absolutePath)
                Log.d("ModelDownload", "Model $modelId downloaded successfully")
                Result.success()
            } else {
                targetFile.delete()
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("ModelDownload", "Failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun downloadFile(urlStr: String, targetFile: File, modelId: String) {
        var connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 300000
        connection.instanceFollowRedirects = true

        // Follow redirects (Hugging Face uses them)
        var redirectCount = 0
        while ((connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    connection.responseCode == 307 || connection.responseCode == 308) && redirectCount < 5) {
            val newUrl = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            connection = URL(newUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 300000
            redirectCount++
        }

        val fileSize = connection.contentLengthLong
        val tmpFile = File(targetFile.parent, "${targetFile.name}.tmp")
        var totalRead = 0L
        var lastUpdate = 0L

        connection.inputStream.buffered().use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    if (System.currentTimeMillis() - lastUpdate > 1000) {
                        lastUpdate = System.currentTimeMillis()
                        val progress = if (fileSize > 0) ((totalRead * 100) / fileSize).toInt() else -1
                        setProgress(Data.Builder()
                            .putInt("progress", progress)
                            .putString("model_id", modelId)
                            .putLong("downloaded", totalRead)
                            .putLong("total", fileSize)
                            .build())
                    }
                }
            }
        }

        connection.disconnect()
        if (tmpFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
        }
    }

    private suspend fun activateModel(modelId: String, modelPath: String) {
        modelCatalogDao.deactivateAll()
        modelCatalogDao.activate(modelId, System.currentTimeMillis())
        localLlmProvider.setActiveModel(modelPath, modelId)
    }
}
