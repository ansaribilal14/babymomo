package com.babymomo.app.ui.screens.mcp

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
import com.babymomo.app.core.mcp.McpServerRegistry
import com.babymomo.app.data.db.entities.McpServerEntity
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class McpUiState(val servers: List<McpServerEntity> = emptyList(), val showAddDialog: Boolean = false, val newName: String = "", val newUrl: String = "")

@HiltViewModel
class McpViewModel @Inject constructor(
    private val mcpServerRegistry: McpServerRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(McpUiState())
    val uiState: StateFlow<McpUiState> = _uiState

    fun showAddDialog() = _uiState.update { it.copy(showAddDialog = true) }
    fun hideAddDialog() = _uiState.update { it.copy(showAddDialog = false, newName = "", newUrl = "") }
    fun onNameChange(name: String) = _uiState.update { it.copy(newName = name) }
    fun onUrlChange(url: String) = _uiState.update { it.copy(newUrl = url) }

    fun addServer() {
        viewModelScope.launch {
            mcpServerRegistry.addServer(_uiState.value.newName, _uiState.value.newUrl)
            hideAddDialog()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(navController: NavController, viewModel: McpViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("MCP Servers", color = ElectricTeal) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack))

        Text("Curated Servers", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = ElectricTeal)

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Fetch", "DeepWiki", "Context7")) { name ->
                Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = PureWhite)
                            Text("Curated MCP server", color = DimBlue, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { /* connect */ }, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                            Text("Connect")
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            FloatingActionButton(onClick = viewModel::showAddDialog, containerColor = ElectricTeal) {
                Text("+", color = MidnightBlack)
            }
        }
    }

    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideAddDialog,
            title = { Text("Add MCP Server", color = PureWhite) },
            containerColor = DeepNavy,
            text = {
                Column {
                    OutlinedTextField(value = uiState.newName, onValueChange = viewModel::onNameChange, label = { Text("Name") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = uiState.newUrl, onValueChange = viewModel::onUrlChange, label = { Text("URL") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::addServer, enabled = uiState.newName.isNotBlank() && uiState.newUrl.isNotBlank()) {
                    Text("Add", color = ElectricTeal)
                }
            }
        )
    }
}
