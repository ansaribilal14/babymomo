package com.babymomo.app.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.Progress
import com.babymomo.app.core.llm.LocalLlmProvider
import com.babymomo.app.data.db.dao.ModelCatalogDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

            // Skip if already downloaded
            if (targetFile.exists() && targetFile.length() > 0) {
                modelCatalogDao.markDownloaded(modelId, System.currentTimeMillis())
                activateModel(modelId, targetFile.absolutePath)
                return Result.success()
            }

            // Download the model file
            withContext(Dispatchers.IO) {
                downloadFile(downloadUrl, targetFile, modelId)
            }

            // Verify download
            if (targetFile.exists() && targetFile.length() > 0) {
                modelCatalogDao.markDownloaded(modelId, System.currentTimeMillis())
                activateModel(modelId, targetFile.absolutePath)
                Log.d("ModelDownload", "Model $modelId downloaded successfully to ${targetFile.absolutePath}")
                Result.success()
            } else {
                Log.e("ModelDownload", "Downloaded file is empty or missing")
                targetFile.delete()
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("ModelDownload", "Failed to download model $modelId: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun downloadFile(urlStr: String, targetFile: File, modelId: String) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 300000
        connection.instanceFollowRedirects = true

        // Handle redirects (Hugging Face uses redirects)
        var responseCode = connection.responseCode
        var currentConnection = connection
        var redirectCount = 0

        while ((responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) && redirectCount < 5) {
            val newUrl = currentConnection.getHeaderField("Location") ?: break
            currentConnection.disconnect()
            currentConnection = URL(newUrl).openConnection() as HttpURLConnection
            currentConnection.connectTimeout = 30000
            currentConnection.readTimeout = 300000
            responseCode = currentConnection.responseCode
            redirectCount++
        }

        val fileSize = currentConnection.contentLengthLong
        val tmpFile = File(targetFile.parent, "${targetFile.name}.tmp")
        var totalRead = 0L
        var lastProgressUpdate = 0L

        currentConnection.inputStream.buffered().use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    // Report progress every 1MB
                    if (System.currentTimeMillis() - lastProgressUpdate > 1000) {
                        lastProgressUpdate = System.currentTimeMillis()
                        val progress = if (fileSize > 0) {
                            ((totalRead * 100) / fileSize).toInt()
                        } else {
                            -1 // Unknown size
                        }
                        setProgress(
                            Data.Builder()
                                .putInt("progress", progress)
                                .putString("model_id", modelId)
                                .putLong("downloaded", totalRead)
                                .putLong("total", fileSize)
                                .build()
                        )
                    }
                }
            }
        }

        currentConnection.disconnect()

        // Rename temp file to final
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
