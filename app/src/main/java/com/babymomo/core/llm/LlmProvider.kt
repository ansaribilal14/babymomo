package com.babymomo.core.llm

import kotlinx.coroutines.flow.Flow

data class LlmMessage(val role: LlmRole, val content: String) {
    companion object {
        fun system(text: String) = LlmMessage(LlmRole.SYSTEM, text)
        fun user(text: String)   = LlmMessage(LlmRole.USER, text)
        fun assistant(text: String) = LlmMessage(LlmRole.ASSISTANT, text)
    }
}

enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

data class LlmGenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxTokens: Int = 1024,
    val stopSequences: List<String> = emptyList(),
    val seed: Long? = null
)

data class LlmResponse(
    val content: String,
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val latencyMs: Long = 0,
    val finishReason: FinishReason = FinishReason.STOP,
    val providerName: String,
    val modelName: String
) {
    enum class FinishReason { STOP, LENGTH, TOOL_CALL, ERROR }
}

/** LLMProvider — interface shape adopted from OpenDroid (Apache-2.0). Streaming is REAL. */
interface LlmProvider {
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun status(): String
    suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig = LlmGenerationConfig()): Result<LlmResponse>
    fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
}
