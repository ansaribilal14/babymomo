package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babymomo.data.db.entity.MemoryEntityLink

@Dao
interface MemoryEntityLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: MemoryEntityLink)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<MemoryEntityLink>)

    @Query("SELECT * FROM memory_entity_links WHERE memoryId = :memoryId")
    suspend fun entitiesForMemory(memoryId: String): List<MemoryEntityLink>

    @Query("SELECT * FROM memory_entity_links WHERE entityId = :entityId")
    suspend fun memoriesForEntity(entityId: String): List<MemoryEntityLink>

    @Query("SELECT memoryId FROM memory_entity_links WHERE entityId IN (:entityIds) GROUP BY memoryId")
    suspend fun memoryIdsForEntities(entityIds: List<String>): List<String>
}
