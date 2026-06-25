package com.babymomo.app.core.llm

import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmChain @Inject constructor(
    private val localProvider: LocalLlmProvider,
    private val remoteProvider: RemoteLlmProvider,
    private val mockProvider: MockLlmProvider
) {
    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk> = flow {
        // Priority 1: Local (LiteRT)
        if (localProvider.isAvailable()) {
            emitAll(localProvider.streamChat(systemPrompt, messages, tools))
            return@flow
        }

        // Priority 2: Remote (OpenAI / NVIDIA NIM / OpenRouter)
        if (remoteProvider.isAvailable()) {
            try {
                emitAll(remoteProvider.streamChat(systemPrompt, messages, tools))
                return@flow
            } catch (_: Exception) {
                // Fall through to mock
            }
        }

        // Priority 3: Mock (always available)
        emitAll(mockProvider.streamChat(systemPrompt, messages, tools))
    }

    suspend fun complete(prompt: String): String {
        if (localProvider.isAvailable()) {
            val result = localProvider.complete(prompt)
            if (result.isNotEmpty()) return result
        }

        if (remoteProvider.isAvailable()) {
            try {
                return remoteProvider.complete(prompt)
            } catch (_: Exception) { }
        }

        return mockProvider.complete(prompt)
    }
}
