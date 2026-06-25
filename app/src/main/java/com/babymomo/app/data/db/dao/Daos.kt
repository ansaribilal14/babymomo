package com.babymomo.app.data.db.dao

import androidx.room.*
import com.babymomo.app.data.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(conversationId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE validTo IS NULL ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE type = :type AND validTo IS NULL ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE isInSystemPrompt = 1")
    suspend fun getPromoted(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("UPDATE memories SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun incrementHitCount(id: String)

    @Query("UPDATE memories SET isInSystemPrompt = 1, validTo = :validTo WHERE id = :id")
    suspend fun promote(id: String, validTo: Long)

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' AND validTo IS NULL")
    suspend fun search(query: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM memories WHERE validTo IS NULL")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE isInSystemPrompt = 1")
    suspend fun getPromotedCount(): Int
}

@Dao
interface MemoryVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: MemoryVectorEntity)

    @Query("SELECT * FROM memory_vectors")
    suspend fun getAll(): List<MemoryVectorEntity>

    @Query("DELETE FROM memory_vectors WHERE memoryId = :memoryId")
    suspend fun deleteByMemoryId(memoryId: String)
}

@Dao
interface EntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EntityEntity)

    @Query("SELECT * FROM entities ORDER BY createdAt DESC")
    fun getAll(): Flow<List<EntityEntity>>

    @Query("SELECT * FROM entities WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<EntityEntity>

    @Query("SELECT COUNT(*) FROM entities")
    suspend fun getCount(): Int

    @Query("DELETE FROM entities WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: RelationEntity)

    @Query("SELECT * FROM relations WHERE fromEntityId = :entityId OR toEntityId = :entityId")
    suspend fun getByEntity(entityId: String): List<RelationEntity>

    @Query("SELECT COUNT(*) FROM relations")
    suspend fun getCount(): Int

    @Query("DELETE FROM relations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ModelCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelCatalogEntity)

    @Query("SELECT * FROM model_catalog")
    fun getAll(): Flow<List<ModelCatalogEntity>>

    @Query("SELECT * FROM model_catalog WHERE isActive = 1")
    suspend fun getActive(): ModelCatalogEntity?

    @Query("UPDATE model_catalog SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE model_catalog SET isActive = 1, isDownloaded = 1, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun activate(id: String, downloadedAt: Long? = null)

    @Query("UPDATE model_catalog SET isDownloaded = 1, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun markDownloaded(id: String, downloadedAt: Long)
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: SettingsEntity)

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingsEntity?

    @Query("SELECT * FROM settings")
    suspend fun getAll(): List<SettingsEntity>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface HeartbeatLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HeartbeatLogEntity)

    @Query("SELECT * FROM heartbeat_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HeartbeatLogEntity>>

    @Query("DELETE FROM heartbeat_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}

@Dao
interface McpServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: McpServerEntity)

    @Query("SELECT * FROM mcp_servers ORDER BY addedAt DESC")
    fun getAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE isEnabled = 1")
    suspend fun getEnabled(): List<McpServerEntity>

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE mcp_servers SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
