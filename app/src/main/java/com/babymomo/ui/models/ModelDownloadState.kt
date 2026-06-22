package com.babymomo.ui.models

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.babymomo.work.ModelDownloadWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * UI-facing state machine derived from [WorkInfo] for a single model download.
 *
 * The Compose layer collects [ModelDownloadState.observe] for each model card and maps the
 * resulting [DownloadState] to a progress bar / cancel button / retry affordance.
 */
sealed class DownloadState {
    /** No work exists (or was cancelled and cleared) for this model id. */
    object Idle : DownloadState()

    /** Active transfer in progress. [totalBytes] is 0 until the server sends Content-Length. */
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()

    /** File fully downloaded, MD5 verification (if any) in progress. */
    object Verifying : DownloadState()

    /** Worker returned success; the model's DB row should already be READY. */
    object Complete : DownloadState()

    /** Worker failed permanently (e.g. HTTP 404, MD5 mismatch). Transient errors retry internally
     *  via [androidx.work.Result.retry] and stay in [Downloading] from the UI's perspective. */
    data class Failed(val message: String) : DownloadState()
}

/**
 * Tiny helper that wraps [WorkManager.getWorkInfosForUniqueWorkFlow] and maps the latest
 * [WorkInfo] for a given [modelId] to a [DownloadState]. The Flow is cold — collect on the
 * Compose side with `collectAsStateWithLifecycle(initialValue = DownloadState.Idle)`.
 */
object ModelDownloadState {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(context: Context, modelId: String): Flow<DownloadState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.uniqueName(modelId))
            .mapLatest { infos ->
                val info = infos.firstOrNull() ?: return@mapLatest DownloadState.Idle
                mapWorkInfo(info)
            }

    private fun mapWorkInfo(info: WorkInfo): DownloadState {
        val progress = info.progress
        val phase = progress.getString(ModelDownloadWorker.KEY_PHASE)
        val bytes = progress.getLong(ModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
        val total = progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L)

        return when (info.state) {
            WorkInfo.State.RUNNING -> when (phase) {
                ModelDownloadWorker.PHASE_VERIFYING -> DownloadState.Verifying
                ModelDownloadWorker.PHASE_COMPLETE -> DownloadState.Complete
                else -> DownloadState.Downloading(bytesDownloaded = bytes, totalBytes = total)
            }
            WorkInfo.State.ENQUEUED -> DownloadState.Downloading(bytesDownloaded = 0L, totalBytes = 0L)
            WorkInfo.State.SUCCEEDED -> DownloadState.Complete
            WorkInfo.State.FAILED -> DownloadState.Failed(
                info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
            )
            WorkInfo.State.CANCELLED,
            WorkInfo.State.BLOCKED -> DownloadState.Idle
        }
    }
}
