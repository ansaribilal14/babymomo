package com.babymomo.app.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(val models: List<ModelCatalogEntity> = emptyList(), val isLoading: Boolean = false)

@HiltViewModel
class ModelsViewModel @Inject constructor(private val modelManager: ModelManager) : ViewModel() {
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

    fun activateModel(id: String) {
        viewModelScope.launch { modelManager.activateModel(id) }
    }

    fun deactivateModel() {
        viewModelScope.launch { modelManager.deactivateModel() }
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

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator(color = ElectricTeal)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.models, key = { it.id }) { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceNavy,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                    Text("${model.sizeBytes / 1_000_000} MB", style = MaterialTheme.typography.bodySmall, color = DimBlue)
                                }
                                if (model.isActive) {
                                    Surface(color = ElectricTeal.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = ElectricTeal)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!model.isDownloaded) {
                                    OutlinedButton(onClick = { /* Download */ }, colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricTeal)) {
                                        Text("Download")
                                    }
                                }
                                if (model.isDownloaded && !model.isActive) {
                                    Button(onClick = { viewModel.activateModel(model.id) }, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                                        Text("Activate")
                                    }
                                }
                                if (model.isActive) {
                                    OutlinedButton(onClick = viewModel::deactivateModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)) {
                                        Text("Deactivate")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
