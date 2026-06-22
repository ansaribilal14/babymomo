package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class EntityType { PERSON, PROJECT, GOAL, SKILL, PLACE, EVENT, IDEA, FILE, NOTE }

@Entity(
    tableName = "entities",
    indices = [Index("type"), Index("canonicalName", unique = true), Index("name")]
)
data class EntityEntity(
    @PrimaryKey val id: String,
    val type: EntityType,
    val name: String,
    val canonicalName: String,
    val aliasesCsv: String = "",
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
