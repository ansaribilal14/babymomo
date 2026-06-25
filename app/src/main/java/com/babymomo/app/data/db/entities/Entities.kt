package com.babymomo.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index("updatedAt")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val routingReason: String? = null,
    val imageUri: String? = null
)

@Entity(
    tableName = "memories",
    indices = [Index("type"), Index("isInSystemPrompt"), Index("validFrom")]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val type: String,  // WORKING, EPISODIC, SEMANTIC, PROCEDURAL
    val confidence: Double = 1.0,
    val hitCount: Int = 0,
    val isInSystemPrompt: Boolean = false,
    val validFrom: Long,
    val validTo: Long? = null,
    val createdAt: Long,
    val sourceMessageId: String? = null
)

@Entity(
    tableName = "memory_vectors",
    foreignKeys = [ForeignKey(
        entity = MemoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["memoryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("memoryId")]
)
data class MemoryVectorEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val embedding: ByteArray,
    val dimension: Int = 384
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

@Entity(
    tableName = "entities",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("type"), Index("projectId")]
)
data class EntityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,  // PERSON, PLACE, CONCEPT, PROJECT, THING
    val description: String? = null,
    val createdAt: Long,
    val projectId: String? = null
)

@Entity(
    tableName = "relations",
    foreignKeys = [
        ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["fromEntityId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntityEntity::class, parentColumns = ["id"], childColumns = ["toEntityId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("fromEntityId"), Index("toEntityId")]
)
data class RelationEntity(
    @PrimaryKey val id: String,
    val fromEntityId: String,
    val toEntityId: String,
    val type: String,
    val weight: Double = 1.0,
    val validFrom: Long,
    val validTo: Long? = null
)

@Entity(
    tableName = "projects",
    indices = [Index("status")]
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE",
    val tasks: String? = null,  // JSON array of task strings
    val graphEntityId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "model_catalog")
data class ModelCatalogEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filename: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false,
    val downloadedAt: Long? = null
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "heartbeat_log", indices = [Index("timestamp")])
data class HeartbeatLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val summary: String,
    val notified: Boolean = false,
    val message: String? = null
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isCurated: Boolean = false,
    val addedAt: Long
)
