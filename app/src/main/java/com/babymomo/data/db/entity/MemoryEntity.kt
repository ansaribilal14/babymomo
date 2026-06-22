package com.babymomo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryType { WORKING, EPISODIC, SEMANTIC, PROCEDURAL }
enum class MemorySource { USER_STATED, LLM_INFERRED, SENSOR, IMPORTED, DERIVED }

/**
 * The atomic unit of long-term memory. Bi-temporal model adapted from Zep/Graphiti:
 *  - createdAt  = ingestion time
 *  - validFrom  = event time (when fact became true in the world)
 *  - validUntil = invalidation time (null = currently true; non-null = superseded)
 *  - supersededBy = FK to the memory that replaced this one
 *
 * Facts are NEVER deleted — they are invalidated. Enables time-travel queries.
 *
 * Embeddings stored inline as ByteArray (384 floats × 4 bytes = 1536 bytes per row).
 */
@Entity(
    tableName = "memories",
    indices = [
        Index("type"), Index("namespace"), Index("validUntil"),
        Index("createdAt"), Index("source"), Index("confidence")
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: MemoryType,
    val content: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val embedding: ByteArray,
    val embeddingDims: Int = 384,
    val source: MemorySource,
    val confidence: Float,
    val namespace: String = "default",
    val createdAt: Long,
    val validFrom: Long,
    val validUntil: Long? = null,
    val supersededBy: String? = null,
    val ttlHours: Int = -1,
    val sourceMemoryId: String? = null,
    val tags: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntity) return false
        return id == other.id
    }
    override fun hashCode(): Int = id.hashCode()
}
