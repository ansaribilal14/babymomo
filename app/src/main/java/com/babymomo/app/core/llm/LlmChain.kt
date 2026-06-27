package com.babymomo.app.core.llm

import android.util.Log
import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Babymomo LLM Chain — Kai pattern.
 *
 * Priority 1: ON-DEVICE (LiteRT) — The brain lives on your device.
 * Priority 2: REMOTE (user-configured OpenAI/NIM/OpenRouter keys)
 * Priority 3: FALLBACK REMOTE — Free API so the app works on first launch
 *
 * The app MUST work on first launch. No dead states.
 * On-device model downloads in the background while remote AI is used.
 * API keys are OPTIONAL.
 */
@Singleton
class LlmChain @Inject constructor(
    private val localProvider: LocalLlmProvider,
    private val remoteProvider: RemoteLlmProvider
) {
    companion object {
        private const val TAG = "LlmChain"
    }

    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk> = flow {
        // Priority 1: On-device LiteRT — this IS Babymomo's brain
        if (localProvider.isAvailable()) {
            Log.d(TAG, "Trying on-device LiteRT...")
            var providerFailed = false
            val tokens = StringBuilder()

            try {
                localProvider.streamChat(systemPrompt, messages, tools).collect { chunk ->
                    when (chunk) {
                        is LlmChunk.Token -> {
                            tokens.append(chunk.text)
                            emit(chunk)
                        }
                        is LlmChunk.Done -> {
                            if (tokens.isNotEmpty()) {
                                Log.d(TAG, "On-device response complete (${tokens.length} chars)")
                                emit(LlmChunk.Done)
                                return@flow
                            }
                            // Got Done but no tokens — provider was empty, fall through
                            providerFailed = true
                        }
                        is LlmChunk.Error -> {
                            Log.d(TAG, "On-device failed: ${chunk.message}")
                            providerFailed = true
                        }
                        else -> { emit(chunk) }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "On-device exception: ${e.message}")
                providerFailed = true
            }

            if (!providerFailed && tokens.isNotEmpty()) return@flow
        }

        // Priority 2: User-configured remote (OpenAI / NVIDIA NIM / OpenRouter)
        if (remoteProvider.isAvailable()) {
            Log.d(TAG, "Trying user remote provider...")
            var providerFailed = false
            val tokens = StringBuilder()

            try {
                remoteProvider.streamChat(systemPrompt, messages, tools).collect { chunk ->
                    when (chunk) {
                        is LlmChunk.Token -> {
                            tokens.append(chunk.text)
                            emit(chunk)
                        }
                        is LlmChunk.Done -> {
                            if (tokens.isNotEmpty()) {
                                Log.d(TAG, "User remote response complete (${tokens.length} chars)")
                                emit(LlmChunk.Done)
                                return@flow
                            }
                            providerFailed = true
                        }
                        is LlmChunk.Error -> {
                            Log.d(TAG, "User remote failed: ${chunk.message}")
                            providerFailed = true
                        }
                        else -> { emit(chunk) }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "User remote exception: ${e.message}")
                providerFailed = true
            }

            if (!providerFailed && tokens.isNotEmpty()) return@flow
        }

        // Priority 3: Fallback — the app MUST respond, never leave the user hanging
        // Free endpoint, no API key needed — works on FIRST LAUNCH
        Log.d(TAG, "Trying free fallback provider...")
        try {
            var gotTokens = false
            remoteProvider.streamWithFallback(systemPrompt, messages, tools).collect { chunk ->
                when (chunk) {
                    is LlmChunk.Token -> {
                        gotTokens = true
                        emit(chunk)
                    }
                    is LlmChunk.Done -> {
                        if (gotTokens) {
                            Log.d(TAG, "Fallback response complete")
                        }
                        emit(LlmChunk.Done)
                        return@flow
                    }
                    is LlmChunk.Error -> {
                        Log.d(TAG, "Fallback failed: ${chunk.message}")
                        // Don't re-throw — fall through to honest message
                    }
                    else -> { emit(chunk) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback exception: ${e.message}")
        }

        // Absolute last resort — this should almost never happen
        emit(LlmChunk.Token(
            "I'm Babymomo — your private AI companion. I'm still getting set up on your device. " +
            "Please add an API key in Settings (OpenAI, NVIDIA NIM, or OpenRouter) and I'll start working immediately. " +
            "An on-device model is also downloading in the background for full offline use."
        ))
        emit(LlmChunk.Done)
    }

    suspend fun complete(prompt: String): String {
        // Priority 1: On-device
        if (localProvider.isAvailable()) {
            try {
                val result = localProvider.complete(prompt)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) { }
        }

        // Priority 2: User remote
        if (remoteProvider.isAvailable()) {
            try {
                val result = remoteProvider.complete(prompt)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) { }
        }

        // Priority 3: Fallback remote
        return try {
            val result = remoteProvider.completeWithFallback(prompt)
            if (result.isNotEmpty()) result else "Setting up... Please add an API key in Settings."
        } catch (_: Exception) {
            "Setting up... Please add an API key in Settings."
        }
    }
}
