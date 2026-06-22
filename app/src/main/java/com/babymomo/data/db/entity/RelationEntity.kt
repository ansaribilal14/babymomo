package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RelationType {
    WORKS_AT, OWNS, INTERESTED_IN, MEMBER_OF,
    DEPENDS_ON, MENTIONS, DERIVED_FROM,
    FRIEND_OF, FAMILY_OF, LEADS, PARTICIPATES_IN,
    LOCATED_IN, HAPPENED_ON, PARENT_OF, CHILD_OF,
    RELATED_TO
}

@Entity(
    tableName = "relations",
    indices = [
        Index("sourceEntityId"), Index("targetEntityId"),
        Index("type"), Index("validUntil"), Index("validFrom")
    ]
)
data class RelationEntity(
    @PrimaryKey val id: String,
    val sourceEntityId: String,
    val targetEntityId: String,
    val type: RelationType,
    val confidence: Float,
    val validFrom: Long,
    val validUntil: Long? = null,
    val sourceMemoryId: String?,
    val createdAt: Long
)
