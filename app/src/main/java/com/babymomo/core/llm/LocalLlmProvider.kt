package com.babymomo.core.llm

import com.babymomo.data.db.entity.ModelRuntime
import com.babymomo.data.db.entity.ModelStatus
import com.babymomo.model.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalLlmProvider — on-device LLM inference.
 *
 * Dispatch table (keyed off the active model's [ModelRuntime]):
 *  - [ModelRuntime.MEDIAPIPE_GENAI] → [MediapipeLlmEngine] (real on-device Gemma inference via
 *    MediaPipe GenAI). Wired in v0.2.
 *  - [ModelRuntime.LLAMA_CPP] → NOT wired yet (returns the "v0.2 wires this" stub error).
 *    Tracked separately — MediaPipe GenAI was chosen as the first runtime because it ships
 *    pre-built .so per ABI and supports Gemma out of the box.
 *  - Other runtimes (MLC_LLM, ONNX_RUNTIME) → NOT wired yet.
 *
 * The LlmProviderChain only calls [complete] / [streamComplete] when [isAvailable] is true, so
 * non-MediaPipe runtimes effectively fall through to Remote / Mock. The stub error paths below
 * exist so that anyone calling [complete] directly (bypassing the chain) gets a clear message.
 */
@Singleton
class LocalLlmProvider @Inject constructor(
    private val modelManager: ModelManager,
    private val mediapipe: MediapipeLlmEngine
) : LlmProvider {
    override val name: String = "local"

    override suspend fun isAvailable(): Boolean {
        val model = modelManager.activeModelFlow().first() ?: return false
        return model.status == ModelStatus.READY &&
            !model.localPath.isNullOrBlank() &&
            model.runtime == ModelRuntime.MEDIAPIPE_GENAI
    }

    override suspend fun status(): String {
        val m = modelManager.activeModelFlow().first() ?: return "No local model loaded — download one from the Models tab"
        val pathTail = m.localPath?.substringAfterLast('/') ?: "(not downloaded)"
        return when (m.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI ->
                if (m.status == ModelStatus.READY && !m.localPath.isNullOrBlank())
                    "Local [MediaPipe GenAI]: ${m.displayName} ($pathTail)"
                else
                    "Local [MediaPipe GenAI]: ${m.displayName} — not downloaded yet"
            ModelRuntime.LLAMA_CPP ->
                "Local [llama.cpp — pending v0.2]: ${m.displayName} ($pathTail)"
            ModelRuntime.MLC_LLM ->
                "Local [MLC LLM — pending]: ${m.displayName} ($pathTail)"
            ModelRuntime.ONNX_RUNTIME ->
                "Local [ONNX Runtime — pending]: ${m.displayName} ($pathTail)"
            else -> "Local: ${m.displayName} ($pathTail)"
        }
    }

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> {
        val model = modelManager.activeModelFlow().first()
            ?: return Result.failure(IllegalStateException("No local model"))
        val path = model.localPath
        if (model.status != ModelStatus.READY || path.isNullOrBlank())
            return Result.failure(IllegalStateException("Active model not downloaded"))

        return when (model.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI -> {
                runCatching {
                    mediapipe.configure(path) // idempotent — reloads only if path changed
                    val prompt = formatGemmaPrompt(messages)
                    val t0 = System.currentTimeMillis()
                    val content = mediapipe.complete(prompt, config)
                    LlmResponse(
                        content = content,
                        tokensIn = (prompt.length / 4).coerceAtLeast(1),
                        tokensOut = (content.length / 4).coerceAtLeast(1),
                        latencyMs = System.currentTimeMillis() - t0,
                        providerName = name,
                        modelName = model.displayName
                    )
                }
            }
            // LLAMA_CPP / MLC_LLM / ONNX_RUNTIME are wired in a later PR — surface a clear stub.
            else -> Result.failure(IllegalStateException(
                "Local inference runtime '${model.runtime}' is not wired yet (v0.2 wires MediaPipe GenAI). " +
                    "Model at $path is downloaded but its runtime bridge is pending."
            ))
        }
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> = flow {
        val model = runBlocking { modelManager.activeModelFlow().first() }
        if (model == null) {
            emit("[No local model — download one from the Models tab]")
            return@flow
        }
        val path = model.localPath
        if (model.status != ModelStatus.READY || path.isNullOrBlank()) {
            emit("[Active model not downloaded — use the Models tab to download ${model.displayName}]")
            return@flow
        }

        when (model.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI -> {
                // Engine is configured eagerly (must happen on a suspend context) before we
                // delegate streaming. The streaming Flow itself is cold and dispatched to the
                // engine's channelFlow.
                runCatching { mediapipe.configure(path) }
                    .onFailure { emit("[MediaPipe load failed: ${it.message}]"); return@flow }
                val prompt = formatGemmaPrompt(messages)
                mediapipe.streamComplete(prompt, config).collect { emit(it) }
            }
            else -> {
                emit("[Local inference runtime '${model.runtime}' is wired in a later PR — v0.2 ships MediaPipe GenAI. Your model is downloaded and ready.]")
            }
        }
    }

    /**
     * Format a list of chat messages into a single prompt string using Gemma's chat template.
     * MediaPipe GenAI's LlmInferenceSession.addQueryChunk takes raw text — no template is
     * applied by MediaPipe itself, so we apply Gemma's `<start_of_turn>` / `<end_of_turn>`
     * markers here.
     *
     * Gemma doesn't have a dedicated system role; we prepend any system messages to the
     * immediately-following user turn (standard Gemma convention).
     */
    private fun formatGemmaPrompt(messages: List<LlmMessage>): String {
        val sb = StringBuilder()
        var pendingSystem: String? = null
        for (msg in messages) {
            when (msg.role) {
                LlmRole.SYSTEM -> {
                    pendingSystem = if (pendingSystem == null) msg.content
                                   else "$pendingSystem\n\n${msg.content}"
                }
                LlmRole.USER, LlmRole.TOOL -> {
                    sb.append("<start_of_turn>user\n")
                    if (pendingSystem != null) { sb.append(pendingSystem).append("\n\n"); pendingSystem = null }
                    sb.append(msg.content).append("<end_of_turn>\n")
                }
                LlmRole.ASSISTANT -> {
                    sb.append("<start_of_turn>model\n")
                    if (pendingSystem != null) { sb.append(pendingSystem).append("\n\n"); pendingSystem = null }
                    sb.append(msg.content).append("<end_of_turn>\n")
                }
            }
        }
        // If only system messages were provided (no user turn), wrap them as a user turn.
        if (pendingSystem != null) {
            sb.append("<start_of_turn>user\n").append(pendingSystem).append("<end_of_turn>\n")
        }
        // Trigger the model's turn.
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}
