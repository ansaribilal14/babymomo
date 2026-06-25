package com.babymomo.app.ui.screens.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.app.core.projects.ProjectService
import com.babymomo.app.data.db.dao.ProjectDao
import com.babymomo.app.data.db.entities.ProjectEntity
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(
    val projects: List<ProjectEntity> = emptyList(),
    val showCreateDialog: Boolean = false,
    val newName: String = "",
    val newDescription: String = ""
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    private val projectService: ProjectService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            projectDao.getAll().collect { projects ->
                _uiState.update { it.copy(projects = projects) }
            }
        }
    }

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun hideCreateDialog() = _uiState.update { it.copy(showCreateDialog = false, newName = "", newDescription = "") }

    fun createProject() {
        viewModelScope.launch {
            projectService.createProject(
                name = _uiState.value.newName,
                description = _uiState.value.newDescription.ifBlank { null }
            )
            hideCreateDialog()
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(newName = name) }
    fun onDescriptionChange(desc: String) = _uiState.update { it.copy(newDescription = desc) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController, viewModel: ProjectsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects", color = ElectricTeal) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showCreateDialog,
                containerColor = ElectricTeal
            ) {
                Icon(Icons.Filled.Add, "New Project", tint = MidnightBlack)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.projects, key = { it.id }) { project ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceNavy,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, color = PureWhite, modifier = Modifier.weight(1f))
                            Surface(
                                color = if (project.status == "ACTIVE") ElectricTeal.copy(alpha = 0.2f) else DimBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(project.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (project.status == "ACTIVE") ElectricTeal else DimBlue)
                            }
                        }
                        project.description?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MutedBlue)
                        }
                    }
                }
            }
        }
    }

    // Create dialog
    if (uiState.showCreateDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideCreateDialog,
            title = { Text("New Project", color = PureWhite) },
            containerColor = DeepNavy,
            text = {
                Column {
                    OutlinedTextField(value = uiState.newName, onValueChange = viewModel::onNameChange, label = { Text("Name") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = uiState.newDescription, onValueChange = viewModel::onDescriptionChange, label = { Text("Description") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal))
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::createProject, enabled = uiState.newName.isNotBlank()) {
                    Text("Create", color = ElectricTeal)
                }
            }
        )
    }
}
