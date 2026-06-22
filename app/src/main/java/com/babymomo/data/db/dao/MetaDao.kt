package com.babymomo.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babymomo.data.db.entity.MetaEntity

@Dao
interface MetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: MetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meta: List<MetaEntity>)

    @Query("SELECT value FROM meta WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM meta")
    suspend fun all(): List<MetaEntity>

    @Query("DELETE FROM meta WHERE key = :key")
    suspend fun delete(key: String): Int
}
