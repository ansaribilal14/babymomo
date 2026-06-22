package com.babymomo.core.llm

import com.babymomo.model.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** LocalLlmProvider — on-device LLM inference (v0.2 wires MediaPipe/llama.cpp/MLC). */
@Singleton
class LocalLlmProvider @Inject constructor(private val modelManager: ModelManager) : LlmProvider {
    override val name: String = "local"
    override suspend fun isAvailable(): Boolean = modelManager.activeModelPath() != null
    override suspend fun status(): String {
        val m = modelManager.activeModelPath()
        return if (m == null) "No local model loaded — download one from the Models tab"
               else "Local model: ${m.substringAfterLast('/')}"
    }

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> {
        val path = modelManager.activeModelPath() ?: return Result.failure(IllegalStateException("No local model"))
        return Result.failure(IllegalStateException("Local inference runtime is wired in v0.2. Model at $path is downloaded but the runtime bridge is pending."))
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> = flow {
        emit("[Local inference runtime is wired in v0.2 — your model is downloaded and ready.]")
    }
}
