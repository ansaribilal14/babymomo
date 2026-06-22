package com.babymomo.core.projects

import com.babymomo.data.db.dao.ProjectDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectContextProvider @Inject constructor(private val projectDao: ProjectDao) {
    private val activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectIdFlow: StateFlow<String?> = activeProjectId.asStateFlow()
    fun setActiveProject(id: String?) { activeProjectId.value = id }

    suspend fun currentContext(): String {
        val id = activeProjectId.value ?: return ""
        val p = projectDao.getProject(id) ?: return ""
        return """
            Name: ${p.name}
            Description: ${p.description}
            Status: ${p.status.name.lowercase()}
        """.trimIndent()
    }
}
