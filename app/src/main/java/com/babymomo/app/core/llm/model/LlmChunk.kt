package com.babymomo.app.core.llm.model

import kotlinx.serialization.json.JsonObject

sealed class LlmChunk {
    data class Token(val text: String) : LlmChunk()
    data class ToolCall(
        val id: String,
        val name: String,
        val input: JsonObject
    ) : LlmChunk()
    data class ToolResult(
        val callId: String,
        val result: String
    ) : LlmChunk()
    data object Done : LlmChunk()
    data class Error(val message: String) : LlmChunk()
}
