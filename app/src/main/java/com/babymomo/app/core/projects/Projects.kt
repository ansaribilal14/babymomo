package com.babymomo.app.core.projects

import com.babymomo.app.data.db.dao.ProjectDao
import com.babymomo.app.data.db.entities.ProjectEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectService @Inject constructor(
    private val projectDao: ProjectDao
) {
    suspend fun createProject(name: String, description: String?, tasks: List<String> = emptyList()): ProjectEntity {
        val project = ProjectEntity(
            id = "proj_${System.currentTimeMillis()}_${name.hashCode()}",
            name = name,
            description = description,
            status = "ACTIVE",
            tasks = if (tasks.isNotEmpty()) kotlinx.serialization.json.buildJsonArray { tasks.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }.toString() else null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        projectDao.insert(project)
        return project
    }

    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(id: String) {
        projectDao.delete(id)
    }
}

@Singleton
class ProjectContextProvider @Inject constructor(
    private val projectDao: ProjectDao
) {
    data class ProjectContext(val name: String, val description: String?, val tasks: String?)

    suspend fun getActiveProjectsContext(): List<ProjectContext> {
        return projectDao.getActive().map { proj ->
            ProjectContext(proj.name, proj.description, proj.tasks)
        }
    }
}
