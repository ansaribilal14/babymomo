package com.babymomo.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.babymomo.data.db.dao.ModelDao
import com.babymomo.data.db.entity.ModelStatus
import com.babymomo.model.ModelManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads a single catalog model (GGUF or MediaPipe `.task`) to internal storage
 * with live progress reporting, optional MD5 integrity verification, and a foreground
 * notification so the OS keeps the transfer alive on Android 14+.
 *
 * Enqueue with [enqueue]; observe progress via [WorkManager.getWorkInfosForUniqueWorkFlow]
 * using [uniqueName] (see `ModelDownloadState`).
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val modelManager: ModelManager,
    private val modelDao: ModelDao,
    private val httpClient: OkHttpClient,
    private val settingsRepo: com.babymomo.core.common.SettingsRepository
) : CoroutineWorker(appContext, params) {

    private val modelId: String? = params.inputData.getString(KEY_MODEL_ID)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = this@ModelDownloadWorker.modelId
        if (modelId.isNullOrBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "Missing modelId"))
        }

        val model = modelDao.get(modelId)
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Model not found: $modelId"))

        // Mark as DOWNLOADING and immediately promote to foreground — on Android 14+ we have
        // a 5-second window after start to call setForeground, so do it as early as possible.
        runCatching { modelDao.update(model.copy(status = ModelStatus.DOWNLOADING)) }
        setForegroundSafely(buildForegroundInfo(model.displayName, 0L, model.sizeBytes))

        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val tempFile = File(modelsDir, "$modelId.tmp")
        val finalFile = File(modelsDir, model.filename)
        tempFile.delete()

        val expectedMd5 = model.md5.takeIf { it.isNotBlank() }

        try {
            val requestBuilder = Request.Builder().url(model.downloadUrl)
            // Attach HuggingFace token if the URL is on huggingface.co and a token is set.
            // Required for gated models (Gemma, Llama, etc.). Read-type tokens suffice.
            if (model.downloadUrl.contains("huggingface.co")) {
                val hfToken = settingsRepo.settings.first().hfToken
                if (hfToken.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $hfToken")
                }
            }
            val request = requestBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val permanent = response.code in 400..499
                    runCatching { modelDao.update(model.copy(status = ModelStatus.ERROR)) }
                    val msg = "HTTP ${response.code} ${response.message}"
                    return@withContext if (permanent)
                        Result.failure(workDataOf(KEY_ERROR to msg))
                    else
                        Result.retry()
                }

                val body = response.body
                    ?: run {
                        runCatching { modelDao.update(model.copy(status = ModelStatus.ERROR)) }
                        return@withContext Result.failure(workDataOf(KEY_ERROR to "Empty response body"))
                    }

                val total = body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
                setProgress(workDataOf(
                    KEY_BYTES_DOWNLOADED to 0L,
                    KEY_TOTAL_BYTES to total,
                    KEY_PHASE to PHASE_DOWNLOADING
                ))

                var downloaded = 0L
                var lastReport = 0L
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            downloaded += n
                            // Throttle progress updates to ~ every 512 KB to avoid DB churn.
                            if (downloaded - lastReport >= 512 * 1024 || downloaded == total) {
                                lastReport = downloaded
                                setProgress(workDataOf(
                                    KEY_BYTES_DOWNLOADED to downloaded,
                                    KEY_TOTAL_BYTES to total,
                                    KEY_PHASE to PHASE_DOWNLOADING
                                ))
                                setForegroundSafely(buildForegroundInfo(model.displayName, downloaded, total))
                            }
                        }
                    }
                }
            }

            // Optional MD5 verification.
            if (expectedMd5 != null) {
                setProgress(workDataOf(
                    KEY_BYTES_DOWNLOADED to model.sizeBytes,
                    KEY_TOTAL_BYTES to model.sizeBytes,
                    KEY_PHASE to PHASE_VERIFYING
                ))
                setForegroundSafely(
                    buildForegroundInfo(model.displayName, model.sizeBytes, model.sizeBytes, "Verifying…")
                )
                val actualMd5 = computeMd5(tempFile)
                if (!actualMd5.equals(expectedMd5, ignoreCase = true)) {
                    tempFile.delete()
                    runCatching { modelDao.update(model.copy(status = ModelStatus.ERROR)) }
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to "MD5 mismatch: expected=$expectedMd5 actual=$actualMd5")
                    )
                }
            }

            // Atomic move into final place. Rename may fail across volumes, so fall back to copy+delete.
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            modelManager.markDownloaded(modelId, finalFile.absolutePath)
            setProgress(workDataOf(
                KEY_BYTES_DOWNLOADED to model.sizeBytes,
                KEY_TOTAL_BYTES to model.sizeBytes,
                KEY_PHASE to PHASE_COMPLETE
            ))
            Result.success()
        } catch (ce: CancellationException) {
            // User cancelled (cancelUniqueWork) or system tore down the coroutine. Clean up and
            // surface as Idle on next observe by resetting status. Rethrow so WorkManager marks
            // the work CANCELLED.
            tempFile.delete()
            runCatching { modelDao.update(model.copy(status = ModelStatus.NOT_DOWNLOADED)) }
            throw ce
        } catch (io: IOException) {
            tempFile.delete()
            // Transient network/IO failure. Leave the model status as-is (DOWNLOADING) while
            // WorkManager backs off and retries, so the UI keeps showing the progress affordance
            // instead of flipping to Retry on every attempt. Only surface ERROR once we've
            // exhausted the retry budget, at which point WorkManager would mark us FAILED anyway.
            if (runAttemptCount + 1 >= MAX_RUN_ATTEMPTS) {
                runCatching { modelDao.update(model.copy(status = ModelStatus.ERROR)) }
                Result.failure(workDataOf(KEY_ERROR to (io.message ?: "Network I/O error")))
            } else {
                Result.retry()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            runCatching { modelDao.update(model.copy(status = ModelStatus.ERROR)) }
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: t.javaClass.simpleName)))
        }
    }

    private suspend fun setForegroundSafely(info: ForegroundInfo) {
        runCatching { setForeground(info) }
    }

    private fun buildForegroundInfo(
        displayName: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        textOverride: String? = null
    ): ForegroundInfo {
        ensureChannel(applicationContext)
        val percent = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes).toInt() else 0
        val text = textOverride ?: "Downloading $displayName…"
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Model download")
            .setContentText("$text  ·  $percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, totalBytes <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        // Stable per-model notification id so re-foregrounds replace the previous notification
        // instead of stacking. Mask to a positive 16-bit range to avoid collisions with other
        // workers (MemoryMaintenanceWorker uses its own scheme).
        val notifId = (NOTIFICATION_ID_BASE + (displayName.hashCode() and 0x0FFF))
        return ForegroundInfo(notifId, notif, type)
    }

    companion object {
        const val CHANNEL_ID = "babymomo.model.downloads"
        const val KEY_MODEL_ID = "modelId"
        const val KEY_BYTES_DOWNLOADED = "bytesDownloaded"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"

        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"
        const val PHASE_COMPLETE = "complete"

        private const val NOTIFICATION_ID_BASE = 0x2000
        private const val TAG = "babymomo.model.download"

        /**
         * Mirrors androidx.work.WorkManager's MAX_RUN_ATTEMPT_COUNT (5 by default). Once we've
         * retried this many times for transient I/O, give up and surface a retryable ERROR.
         */
        private const val MAX_RUN_ATTEMPTS = 5

        fun uniqueName(modelId: String): String = "babymomo.model.download.$modelId"

        /** Enqueue a one-shot download for [modelId]. Re-queueing the same id replaces prior work. */
        fun enqueue(context: Context, modelId: String) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to modelId))
                .addTag(TAG)
                .addTag("modelId:$modelId")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(modelId), ExistingWorkPolicy.REPLACE, request)
        }

        /** Cancel an in-flight download for [modelId], if any. No-op if none is running. */
        fun cancel(context: Context, modelId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(modelId))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background downloads of on-device LLM models"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        private fun computeMd5(file: File): String {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = fis.read(buf)
                    if (n == -1) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
