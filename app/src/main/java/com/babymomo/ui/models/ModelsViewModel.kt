package com.babymomo.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(val models: List<ModelEntity> = emptyList(), val activeModel: ModelEntity? = null)

@HiltViewModel
class ModelsViewModel @Inject constructor(private val modelManager: ModelManager) : ViewModel() {
    val state: StateFlow<ModelsUiState> = combine(modelManager.allModelsFlow(), modelManager.activeModelFlow()) { models, active ->
        ModelsUiState(models = models, activeModel = active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelsUiState())

    fun activate(modelId: String) { viewModelScope.launch { modelManager.activate(modelId) } }

    fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0); val gb = mb / 1024.0
        return if (gb >= 1) String.format("%.1f GB", gb) else String.format("%.0f MB", mb)
    }
}
