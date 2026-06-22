package com.babymomo.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }
enum class MessageStatus { PENDING, STREAMING, COMPLETE, ERROR }

@Entity(tableName = "messages", indices = [Index("conversationId"), Index("createdAt")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val modelId: String? = null,
    val latencyMs: Long = 0,
    val citedMemoryIds: String = ""
)
