package com.babymomo.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.core.projects.ProjectService
import com.babymomo.data.db.dao.ProjectDao
import com.babymomo.data.db.dao.TaskDao
import com.babymomo.data.db.entity.ProjectEntity
import com.babymomo.data.db.entity.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectsUiState(val projects: List<ProjectEntity> = emptyList(), val showCreateDialog: Boolean = false)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectService: ProjectService, private val projectDao: ProjectDao, private val taskDao: TaskDao
) : ViewModel() {
    private val _showCreate = MutableStateFlow(false)

    val state: StateFlow<ProjectsUiState> = combine(projectService.activeProjectsFlow(), _showCreate) { projects, showCreate ->
        ProjectsUiState(projects = projects, showCreateDialog = showCreate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsUiState())

    fun showCreateDialog(show: Boolean) { _showCreate.value = show }
    fun createProject(name: String, description: String, tasks: List<String>) {
        viewModelScope.launch { projectService.createProject(name, description, tasks); _showCreate.value = false }
    }
    fun setTaskStatus(taskId: String, status: TaskStatus) { viewModelScope.launch { projectService.setTaskStatus(taskId, status) } }
}
