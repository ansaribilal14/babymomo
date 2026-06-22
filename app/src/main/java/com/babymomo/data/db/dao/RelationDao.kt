package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babymomo.data.db.entity.RelationEntity
import com.babymomo.data.db.entity.RelationType
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relation: RelationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(relations: List<RelationEntity>)

    @Query("SELECT * FROM relations WHERE id = :id")
    suspend fun get(id: String): RelationEntity?

    @Query("SELECT * FROM relations WHERE sourceEntityId = :entityId AND validUntil IS NULL ORDER BY confidence DESC, validFrom DESC")
    suspend fun outgoingCurrent(entityId: String): List<RelationEntity>

    @Query("SELECT * FROM relations WHERE targetEntityId = :entityId AND validUntil IS NULL ORDER BY confidence DESC, validFrom DESC")
    suspend fun incomingCurrent(entityId: String): List<RelationEntity>

    @Query("SELECT * FROM relations WHERE (sourceEntityId IN (:entityIds) OR targetEntityId IN (:entityIds)) AND validUntil IS NULL ORDER BY confidence DESC")
    suspend fun neighborsCurrent(entityIds: List<String>): List<RelationEntity>

    @Query("UPDATE relations SET validUntil = :now WHERE sourceEntityId = :sourceId AND targetEntityId = :targetId AND type = :type AND validUntil IS NULL")
    suspend fun invalidateEdge(sourceId: String, targetId: String, type: RelationType, now: Long): Int

    @Query("SELECT * FROM relations WHERE validUntil IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 200): Flow<List<RelationEntity>>

    @Query("SELECT COUNT(*) FROM relations WHERE validUntil IS NULL")
    fun activeCountFlow(): Flow<Int>
}
