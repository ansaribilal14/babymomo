package com.babymomo.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.data.db.dao.ModelDao
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelStatus
import com.babymomo.model.ModelManager
import com.babymomo.work.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OnboardingViewModel — drives the first-launch model download.
 *
 * The flow:
 * 1. Check if any model is READY (already downloaded). If yes → skip onboarding.
 * 2. If not, start downloading the default model (SmolLM-135M, 159MB).
 * 3. Observe download progress via WorkManager.
 * 4. When download completes → auto-activate the model → signal UI to proceed to Chat.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application,
    private val modelManager: ModelManager,
    private val modelDao: ModelDao
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Checking)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        checkAndStartDownload()
    }

    private fun checkAndStartDownload() {
        viewModelScope.launch {
            // Seed catalog first (ensures the default model entry exists)
            modelManager.seedCatalogIfEmpty()

            // Check if default model is already downloaded
            val defaultModel = modelDao.get(modelManager.firstLaunchModelId)
            if (defaultModel != null && defaultModel.status == ModelStatus.READY) {
                // Already downloaded — make sure it's active, then proceed
                modelManager.activate(defaultModel.id)
                _state.value = OnboardingState.Ready
                return@launch
            }

            // Start the download
            _state.value = OnboardingState.Downloading(
                progress = 0f,
                downloadedMb = 0L,
                totalMb = defaultModel?.sizeBytes ?: 166_754_726L,
                modelName = defaultModel?.displayName ?: "SmolLM 135M"
            )

            ModelDownloadWorker.enqueue(getApplication(), modelManager.firstLaunchModelId)

            // Observe progress
            observeDownload(modelManager.firstLaunchModelId)
        }
    }

    private fun observeDownload(modelId: String) {
        viewModelScope.launch {
            com.babymomo.ui.models.ModelDownloadState.observe(getApplication(), modelId).collect { dlState ->
                when (dlState) {
                    is com.babymomo.ui.models.DownloadState.Downloading -> {
                        val total = dlState.totalBytes.coerceAtLeast(1)
                        val progress = (dlState.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
                        _state.value = OnboardingState.Downloading(
                            progress = progress,
                            downloadedMb = dlState.bytesDownloaded / 1_048_576,
                            totalMb = dlState.totalBytes / 1_048_576,
                            modelName = "SmolLM 135M"
                        )
                    }
                    is com.babymomo.ui.models.DownloadState.Complete -> {
                        // Download finished — find the model, get its path, activate it
                        val model = modelDao.get(modelId)
                        if (model != null) {
                            val path = java.io.File(getApplication<Application>().filesDir, "models/${model.filename}").absolutePath
                            modelManager.markDownloaded(modelId, path)
                            modelManager.activate(modelId)
                        }
                        _state.value = OnboardingState.Ready
                    }
                    is com.babymomo.ui.models.DownloadState.Failed -> {
                        _state.value = OnboardingState.Error(dlState.message)
                    }
                    else -> { /* Idle, Verifying — keep current state */ }
                }
            }
        }
    }

    fun retry() {
        _state.value = OnboardingState.Checking
        checkAndStartDownload()
    }

    fun skip() {
        _state.value = OnboardingState.Skipped
    }
}

sealed class OnboardingState {
    object Checking : OnboardingState()
    data class Downloading(
        val progress: Float,
        val downloadedMb: Long,
        val totalMb: Long,
        val modelName: String
    ) : OnboardingState()
    object Ready : OnboardingState()
    object Skipped : OnboardingState()
    data class Error(val message: String) : OnboardingState()
}
