package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<ModelEntity>)

    @Update
    suspend fun update(model: ModelEntity)

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun get(id: String): ModelEntity?

    @Query("SELECT * FROM models ORDER BY displayName COLLATE NOCASE")
    fun allFlow(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE status = :status ORDER BY displayName COLLATE NOCASE")
    fun byStatusFlow(status: ModelStatus): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    suspend fun activeModel(): ModelEntity?

    @Query("SELECT * FROM models WHERE isActive = 1 LIMIT 1")
    fun activeModelFlow(): Flow<ModelEntity?>

    @Query("UPDATE models SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAll()

    @Query("UPDATE models SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("UPDATE models SET status = :status, localPath = :path, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun markDownloaded(id: String, status: ModelStatus, path: String, downloadedAt: Long)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)
}
