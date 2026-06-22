package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemorySource
import com.babymomo.data.db.entity.MemoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<MemoryEntity>)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE validUntil IS NULL AND namespace = :namespace AND ttlHours != 0 ORDER BY createdAt DESC")
    suspend fun activeMemories(namespace: String = "default"): List<MemoryEntity>

    @Query("SELECT id, embedding, embeddingDims, content, confidence, validFrom, createdAt, type, source, namespace, ttlHours, sourceMemoryId, supersededBy, tags, validUntil FROM memories WHERE validUntil IS NULL AND namespace = :namespace AND ttlHours != 0")
    suspend fun activeEmbeddings(namespace: String = "default"): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE validUntil IS NULL AND namespace = :namespace ORDER BY createdAt DESC LIMIT :limit")
    fun recentActiveFlow(namespace: String = "default", limit: Int = 200): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE type = :type AND validUntil IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun byTypeFlow(type: MemoryType, limit: Int = 200): Flow<List<MemoryEntity>>

    @Query("UPDATE memories SET validUntil = :now, supersededBy = :byId WHERE id = :memoryId AND validUntil IS NULL")
    suspend fun invalidate(memoryId: String, now: Long, byId: String?): Int

    @Query("UPDATE memories SET validUntil = :now, supersededBy = NULL WHERE id = :memoryId AND validUntil IS NULL")
    suspend fun softDelete(memoryId: String, now: Long): Int

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :q || '%' AND validUntil IS NULL ORDER BY confidence DESC, createdAt DESC LIMIT :limit")
    suspend fun searchContent(q: String, limit: Int = 50): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE validUntil IS NULL")
    fun activeCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM memories")
    fun totalCountFlow(): Flow<Int>

    @Query("DELETE FROM memories WHERE validUntil IS NOT NULL AND validUntil < :cutoff")
    suspend fun purgeInvalidatedBefore(cutoff: Long): Int

    @Query("DELETE FROM memories WHERE content IN ('I don''t know', 'I cannot help', 'As an AI', 'I''m just a language model', '[INVALID]', '[ERROR]', '[UNKNOWN]') AND source = :source")
    suspend fun deletePoisonedFromSource(source: MemorySource = MemorySource.LLM_INFERRED): Int
}
