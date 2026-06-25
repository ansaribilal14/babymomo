package com.babymomo.app.core.llm

import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk>

    suspend fun complete(prompt: String): String

    fun isAvailable(): Boolean

    fun providerName(): String
}
