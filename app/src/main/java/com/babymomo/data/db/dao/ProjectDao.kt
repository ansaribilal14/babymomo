package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babymomo.data.db.entity.ProjectEntity
import com.babymomo.data.db.entity.TaskEntity
import com.babymomo.data.db.entity.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(p: ProjectEntity)

    @Update
    suspend fun updateProject(p: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE status != 'ARCHIVED' ORDER BY updatedAt DESC")
    fun activeProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TaskEntity)

    @Update
    suspend fun update(t: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE projectId = :pid ORDER BY status, position, dueAt")
    fun tasksForProjectFlow(pid: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :pid AND status = :status ORDER BY position")
    suspend fun tasksForProjectByStatus(pid: String, status: TaskStatus): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE tasks SET status = :status, updatedAt = :now, completedAt = :completedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: TaskStatus, now: Long, completedAt: Long? = null)
}
