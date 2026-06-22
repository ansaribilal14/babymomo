package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.EntityType
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EntityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EntityEntity>)

    @Update
    suspend fun update(entity: EntityEntity)

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun get(id: String): EntityEntity?

    @Query("SELECT * FROM entities WHERE canonicalName = :canonical LIMIT 1")
    suspend fun findByCanonicalName(canonical: String): EntityEntity?

    @Query("SELECT * FROM entities WHERE canonicalName = :canonical OR aliasesCsv LIKE '%' || :canonical || '%' LIMIT 1")
    suspend fun matchByAlias(canonical: String): EntityEntity?

    @Query("SELECT * FROM entities WHERE type = :type ORDER BY name COLLATE NOCASE")
    fun byTypeFlow(type: EntityType): Flow<List<EntityEntity>>

    @Query("SELECT * FROM entities ORDER BY name COLLATE NOCASE")
    fun allFlow(): Flow<List<EntityEntity>>

    @Query("SELECT * FROM entities WHERE name LIKE '%' || :q || '%' OR aliasesCsv LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE LIMIT :limit")
    suspend fun search(q: String, limit: Int = 30): List<EntityEntity>

    @Query("SELECT COUNT(*) FROM entities")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM entities WHERE id = :id")
    suspend fun delete(id: String): Int
}
