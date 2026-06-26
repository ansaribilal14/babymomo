package com.babymomo.app.core.llm

import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Babymomo LLM Chain — Kai pattern.
 *
 * Priority 1: ON-DEVICE (LiteRT) — The brain lives on your device. Always first.
 * Priority 2: REMOTE (OpenAI / NVIDIA NIM / OpenRouter) — Optional cloud boost, only if user provides keys.
 * No mock. No fake responses. If nothing is available, we tell the user honestly.
 *
 * API keys are OPTIONAL. The app works fully on-device.
 * Remote providers are a bonus, not a requirement.
 */
@Singleton
class LlmChain @Inject constructor(
    private val localProvider: LocalLlmProvider,
    private val remoteProvider: RemoteLlmProvider
) {
    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk> = flow {
        // Priority 1: On-device LiteRT — this IS Babymomo's brain
        if (localProvider.isAvailable()) {
            try {
                emitAll(localProvider.streamChat(systemPrompt, messages, tools))
                return@flow
            } catch (e: Exception) {
                // On-device failed, try remote as fallback
            }
        }

        // Priority 2: Remote cloud — optional, user-configured, keys are OPTIONAL
        if (remoteProvider.isAvailable()) {
            try {
                emitAll(remoteProvider.streamChat(systemPrompt, messages, tools))
                return@flow
            } catch (e: Exception) {
                // Remote failed too
            }
        }

        // Nothing available — be honest with the user, not fake
        emit(LlmChunk.Error(noProviderMessage()))
    }

    suspend fun complete(prompt: String): String {
        // Try on-device first
        if (localProvider.isAvailable()) {
            try {
                val result = localProvider.complete(prompt)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) { }
        }

        // Try remote
        if (remoteProvider.isAvailable()) {
            try {
                return remoteProvider.complete(prompt)
            } catch (_: Exception) { }
        }

        return noProviderMessage()
    }

    private fun noProviderMessage(): String {
        return if (!localProvider.isAvailable() && !remoteProvider.isAvailable()) {
            "No AI model is available yet. Download an on-device model from the Models tab to get started — everything runs privately on your device, no internet needed. Or optionally add an API key in Settings for cloud AI."
        } else {
            "AI provider encountered an error. Please check your connection or try a different model."
        }
    }
}
