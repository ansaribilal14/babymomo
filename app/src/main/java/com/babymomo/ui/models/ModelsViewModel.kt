package com.babymomo.ui.models

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.model.ModelManager
import com.babymomo.work.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(val models: List<ModelEntity> = emptyList(), val activeModel: ModelEntity? = null)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val state: StateFlow<ModelsUiState> = combine(
        modelManager.allModelsFlow(),
        modelManager.activeModelFlow()
    ) { models, active ->
        ModelsUiState(models = models, activeModel = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelsUiState())

    fun activate(modelId: String) { viewModelScope.launch { modelManager.activate(modelId) } }

    /** Start (or re-start) a background download for [modelId]. Existing work for this model
     *  is replaced, so the user can re-tap after a failure without queueing duplicates. */
    fun downloadModel(modelId: String) {
        ModelDownloadWorker.enqueue(appContext, modelId)
    }

    /** Cancel an in-flight download. The worker cleans up its temp file and resets status. */
    fun cancelDownload(modelId: String) {
        ModelDownloadWorker.cancel(appContext, modelId)
    }

    /** Cold flow of the current download state for [modelId]. Collect on the Compose side. */
    fun downloadStateFlow(modelId: String): Flow<DownloadState> =
        ModelDownloadState.observe(appContext, modelId)

    fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0); val gb = mb / 1024.0
        return if (gb >= 1) String.format("%.1f GB", gb) else String.format("%.0f MB", mb)
    }
}
