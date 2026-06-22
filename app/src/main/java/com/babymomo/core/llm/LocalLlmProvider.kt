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
 * ### v0.2 status: ALL local runtimes are stubbed.
 * The intended dispatch table (keyed off the active model's [ModelRuntime]):
 *  - [ModelRuntime.MEDIAPIPE_GENAI] → [MediapipeLlmEngine]. STUBBED in v0.2: the MediaPipe
 *    `tasks-genai` 0.10.14 artifact does NOT ship the session API this engine was written
 *    against (no `LlmInferenceSession`, no `addQueryChunk`, no streaming partial-result
 *    callback). `MediapipeLlmEngine` throws `IllegalStateException` from every method.
 *    v0.3 will either upgrade MediaPipe or rewrite the engine against the actual 0.10.14
 *    `LlmInference.generateResponse` surface.
 *  - [ModelRuntime.LLAMA_CPP] → NOT wired yet (llama.cpp JNI bridge pending).
 *  - Other runtimes (MLC_LLM, ONNX_RUNTIME) → NOT wired yet.
 *
 * [isAvailable] returns `false` for every runtime in v0.2, so [LlmProviderChain] skips Local
 * entirely and falls through to Remote → Mock. The stub error paths in [complete] /
 * [streamComplete] exist so that anyone calling Local directly (bypassing the chain) gets a
 * clear message instead of a confusing MediaPipe internal error.
 */
@Singleton
class LocalLlmProvider @Inject constructor(
    private val modelManager: ModelManager,
    private val mediapipe: MediapipeLlmEngine
) : LlmProvider {
    override val name: String = "local"

    override suspend fun isAvailable(): Boolean {
        // v0.2: MediaPipe GenAI is stubbed (tasks-genai 0.10.14 API mismatch — see
        // MediapipeLlmEngine KDoc + CHANGELOG [0.2.0] ### Known Issues). All other local
        // runtimes (llama.cpp / MLC / ONNX) are also unwired. So Local is NEVER available
        // in v0.2 — LlmProviderChain falls through to Remote → Mock.
        return false
    }

    override suspend fun status(): String {
        val m = modelManager.activeModelFlow().first() ?: return "No local model loaded — download one from the Models tab"
        val pathTail = m.localPath?.substringAfterLast('/') ?: "(not downloaded)"
        return when (m.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI ->
                "Local [MediaPipe GenAI — stubbed in v0.2, pending v0.3]: ${m.displayName} ($pathTail)"
            ModelRuntime.LLAMA_CPP ->
                "Local [llama.cpp — pending v0.3]: ${m.displayName} ($pathTail)"
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
                // v0.2 stub: MediapipeLlmEngine.configure throws IllegalStateException because
                // the tasks-genai 0.10.14 API doesn't match the engine's session-based design.
                // runCatching surfaces it as a Result.failure so the chain falls through cleanly.
                runCatching {
                    mediapipe.configure(path) // throws (stub) — see MediapipeLlmEngine KDoc
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
                "Local inference runtime '${model.runtime}' is not wired in v0.2. " +
                    "MediaPipe GenAI is stubbed (tasks-genai 0.10.14 API mismatch — see v0.3); " +
                    "other runtimes (llama.cpp / MLC / ONNX) are pending. " +
                    "Model at $path is downloaded but its runtime bridge is unavailable."
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
                // v0.2 stub: MediapipeLlmEngine.configure throws IllegalStateException (stub).
                // Surface the stub message and return — the chain will fall through to Remote/Mock.
                runCatching { mediapipe.configure(path) }
                    .onFailure { emit("[MediaPipe GenAI stubbed in v0.2 — ${it.message}]"); return@flow }
                val prompt = formatGemmaPrompt(messages)
                mediapipe.streamComplete(prompt, config).collect { emit(it) }
            }
            else -> {
                emit("[Local inference runtime '${model.runtime}' is not wired in v0.2 — MediaPipe GenAI is stubbed (tasks-genai 0.10.14 API mismatch, pending v0.3); other runtimes pending. Your model is downloaded but Local inference is unavailable; falling back to Remote/Mock.]")
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
