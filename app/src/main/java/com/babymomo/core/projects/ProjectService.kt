package com.babymomo.core.projects

import com.babymomo.data.db.dao.ProjectDao
import com.babymomo.data.db.dao.TaskDao
import com.babymomo.data.db.entity.ProjectEntity
import com.babymomo.data.db.entity.ProjectStatus
import com.babymomo.data.db.entity.TaskEntity
import com.babymomo.data.db.entity.TaskPriority
import com.babymomo.data.db.entity.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectService @Inject constructor(
    private val projectDao: ProjectDao,
    private val taskDao: TaskDao,
    private val graph: com.babymomo.core.memory.MemoryGraph
) {
    fun activeProjectsFlow(): Flow<List<ProjectEntity>> = projectDao.activeProjectsFlow()
    fun tasksForProjectFlow(projectId: String): Flow<List<TaskEntity>> = taskDao.tasksForProjectFlow(projectId)

    suspend fun createProject(name: String, description: String, initialTasks: List<String> = emptyList()): ProjectEntity {
        val now = System.currentTimeMillis()
        val canonical = name.lowercase().trim().replace(Regex("\\s+"), "_").take(64)
        val project = ProjectEntity(
            id = "proj_" + UUID.randomUUID().toString().take(16),
            name = name.trim(), canonicalName = canonical, description = description,
            status = ProjectStatus.ACTIVE, createdAt = now, updatedAt = now
        )
        projectDao.upsertProject(project)
        val entity = graph.resolveOrCreate(name, com.babymomo.data.db.entity.EntityType.PROJECT, description)
        projectDao.updateProject(project.copy(entityGraphId = entity.id))
        for (taskTitle in initialTasks.filter { it.isNotBlank() }) addTask(project.id, taskTitle)
        return project
    }

    suspend fun addTask(projectId: String, title: String, description: String = "",
                        priority: TaskPriority = TaskPriority.MEDIUM, dueAt: Long? = null): TaskEntity {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            id = "task_" + UUID.randomUUID().toString().take(16),
            projectId = projectId, title = title.trim(), description = description,
            status = TaskStatus.TODO, priority = priority,
            createdAt = now, updatedAt = now, dueAt = dueAt
        )
        taskDao.upsert(task)
        projectDao.getProject(projectId)?.let { projectDao.updateProject(it.copy(updatedAt = now)) }
        return task
    }

    suspend fun setTaskStatus(taskId: String, status: TaskStatus) {
        val now = System.currentTimeMillis()
        val completed = if (status == TaskStatus.DONE) now else null
        taskDao.setStatus(taskId, status, now, completed)
    }

    suspend fun archiveProject(projectId: String) {
        val p = projectDao.getProject(projectId) ?: return
        projectDao.updateProject(p.copy(status = ProjectStatus.ARCHIVED, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(projectId: String) { projectDao.deleteProject(projectId) }
}
