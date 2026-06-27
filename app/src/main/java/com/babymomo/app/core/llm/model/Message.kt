package com.babymomo.app.core.llm.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String,
    val imageUri: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null
) {
    companion object {
        fun user(text: String, imageUri: String? = null) = Message(
            role = "user", content = text, imageUri = imageUri
        )
        fun assistant(text: String) = Message(
            role = "assistant", content = text
        )
        fun tool(callId: String, name: String, result: String) = Message(
            role = "tool", content = result, toolCallId = callId, toolName = name
        )
        fun system(text: String) = Message(
            role = "system", content = text
        )
    }
}
