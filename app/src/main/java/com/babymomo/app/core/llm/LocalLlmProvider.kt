package com.babymomo.app.core.llm

import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmProvider @Inject constructor() : LlmProvider {

    @Volatile
    private var modelLoaded = false

    @Volatile
    private var activeModelPath: String? = null

    fun setActiveModel(modelPath: String?) {
        activeModelPath = modelPath
        modelLoaded = modelPath != null
    }

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        if (!modelLoaded || activeModelPath == null) {
            emit(LlmChunk.Error("No on-device model loaded"))
            return@flow
        }

        // LiteRT bridge - will be fully implemented when model files are available
        // For now, delegate to the next provider in chain
        emit(LlmChunk.Error("On-device model not yet downloaded. Download from the Models tab."))
    }

    override suspend fun complete(prompt: String): String {
        if (!modelLoaded) return ""
        // LiteRT inference will be connected here
        return ""
    }

    override fun isAvailable(): Boolean = modelLoaded

    override fun providerName(): String = "LiteRT"
}
