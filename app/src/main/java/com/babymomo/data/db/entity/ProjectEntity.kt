package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProjectStatus { ACTIVE, ON_HOLD, COMPLETED, ARCHIVED }
enum class TaskStatus { TODO, IN_PROGRESS, DONE, SKIPPED }
enum class TaskPriority { LOW, MEDIUM, HIGH, URGENT }

@Entity(tableName = "projects", indices = [Index("status"), Index("updatedAt"), Index("canonicalName")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val canonicalName: String,
    val description: String,
    val status: ProjectStatus = ProjectStatus.ACTIVE,
    val color: String = "#D97F3F",
    val createdAt: Long,
    val updatedAt: Long,
    val targetDate: Long? = null,
    val parentProjectId: String? = null,
    val entityGraphId: String? = null
)

@Entity(tableName = "tasks", indices = [Index("projectId"), Index("status"), Index("priority"), Index("dueAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val createdAt: Long,
    val updatedAt: Long,
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    val position: Int = 0,
    val sourceConversationId: String? = null,
    val sourceMemoryId: String? = null
)
