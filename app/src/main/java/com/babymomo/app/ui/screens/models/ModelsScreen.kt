package com.babymomo.app.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.babymomo.app.model.ModelManager
import com.babymomo.app.data.db.entities.ModelCatalogEntity
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ModelsUiState(
    val models: List<ModelCatalogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val downloadingModelId: String? = null,
    val downloadProgress: Int = 0
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init { loadModels() }

    fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val models = modelManager.getCatalog()
            _uiState.update { it.copy(models = models, isLoading = false) }
        }
    }

    fun downloadModel(id: String) {
        modelManager.startModelDownload(id)
        _uiState.update { it.copy(downloadingModelId = id, downloadProgress = 0) }
        // Progress is observed from WorkManager in the composable
    }

    fun activateModel(id: String) {
        viewModelScope.launch { modelManager.activateModel(id) }
        loadModels() // Refresh
    }

    fun deactivateModel() {
        viewModelScope.launch { modelManager.deactivateModel() }
        loadModels()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(navController: NavController, viewModel: ModelsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Models", color = ElectricTeal) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack)
        )

        // Info banner
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = ElevatedNavy,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "On-device models run privately — no internet needed after download. " +
                "Babymomo downloads the default model automatically on first start.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MutedBlue
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricTeal)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isDownloading = uiState.downloadingModelId == model.id,
                        downloadProgress = if (uiState.downloadingModelId == model.id) uiState.downloadProgress else 0,
                        onDownload = { viewModel.downloadModel(model.id) },
                        onActivate = { viewModel.activateModel(model.id) },
                        onDeactivate = { viewModel.deactivateModel() }
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelCatalogEntity,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceNavy,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Text("${model.sizeBytes / 1_000_000} MB", style = MaterialTheme.typography.bodySmall, color = DimBlue)
                }
                if (model.isActive) {
                    Surface(color = ElectricTeal.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = ElectricTeal)
                    }
                }
                if (model.isDownloaded && !model.isActive) {
                    Surface(color = MutedBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("Downloaded", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MutedBlue)
                    }
                }
            }

            // Download progress bar
            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = ElectricTeal,
                    trackColor = DividerBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (downloadProgress > 0) "Downloading... $downloadProgress%" else "Starting download...",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!model.isDownloaded && !isDownloading) {
                    Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                        Text("Download")
                    }
                }
                if (model.isDownloaded && !model.isActive) {
                    Button(onClick = onActivate, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                        Text("Activate")
                    }
                }
                if (model.isActive) {
                    OutlinedButton(onClick = onDeactivate, colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                        Text("Deactivate")
                    }
                }
            }
        }
    }
}
