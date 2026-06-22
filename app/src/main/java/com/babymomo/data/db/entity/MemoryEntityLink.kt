package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memory_entity_links",
    primaryKeys = ["memoryId", "entityId"],
    indices = [Index("entityId"), Index("memoryId"), Index("confidence")]
)
data class MemoryEntityLink(
    val memoryId: String,
    val entityId: String,
    val confidence: Float,
    val extractedAt: Long
)
