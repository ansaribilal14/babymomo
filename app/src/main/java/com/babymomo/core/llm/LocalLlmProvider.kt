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
 * ### v0.3 status: MediaPipe GenAI runtime is LIVE for `.task`-format Gemma models.
 * The dispatch table (keyed off the active model's [ModelRuntime]):
 *  - [ModelRuntime.MEDIAPIPE_GENAI] → [MediapipeLlmEngine] (real implementation in v0.3).
 *    Engine configuration (`MediapipeLlmEngine.configure(path)`) happens lazily on the
 *    first `isAvailable()` call that finds a READY + MEDIAPIPE_GENAI active model whose
 *    path differs from `mediapipe.loadedPath()`. `configure` is idempotent for the same
 *    path, so subsequent `isAvailable()` calls are no-ops. The chain transparently picks
 *    Local up the first time a Gemma `.task` model is activated.
 *  - [ModelRuntime.LLAMA_CPP] → NOT wired yet (llama.cpp JNI bridge pending v0.4+).
 *  - Other runtimes (MLC_LLM, ONNX_RUNTIME) → NOT wired yet.
 *
 * Non-MediaPipe runtimes fall through to Remote / Mock exactly as in v0.2.
 */
@Singleton
class LocalLlmProvider @Inject constructor(
    private val modelManager: ModelManager,
    private val mediapipe: MediapipeLlmEngine
) : LlmProvider {
    override val name: String = "local"

    override suspend fun isAvailable(): Boolean {
        val model = modelManager.activeModelFlow().first() ?: return false
        val path = model.localPath
        if (model.status != ModelStatus.READY || path.isNullOrBlank()) return false
        return when (model.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI -> {
                // Lazy-load: if the engine isn't holding this exact path, configure it now.
                // configure() is idempotent for the same path (no-op short-circuit), so
                // repeated isAvailable() calls don't re-create the native engine.
                if (!mediapipe.isLoaded() || mediapipe.loadedPath() != path) {
                    runCatching { mediapipe.configure(path) }
                }
                mediapipe.isLoaded() && mediapipe.loadedPath() == path
            }
            else -> false
        }
    }

    override suspend fun status(): String {
        val m = modelManager.activeModelFlow().first() ?: return "No local model loaded — download one from the Models tab"
        val pathTail = m.localPath?.substringAfterLast('/') ?: "(not downloaded)"
        return when (m.runtime) {
            ModelRuntime.MEDIAPIPE_GENAI -> {
                val state = if (mediapipe.isLoaded() && mediapipe.loadedPath() == m.localPath)
                    "engine loaded" else "engine not yet loaded"
                "Local [MediaPipe GenAI 0.10.35 — $state]: ${m.displayName} ($pathTail)"
            }
            ModelRuntime.LLAMA_CPP ->
                "Local [llama.cpp — pending v0.4+]: ${m.displayName} ($pathTail)"
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
            ModelRuntime.MEDIAPIPE_GENAI -> runCatching {
                // Lazy-load on direct calls too (defensive — isAvailable() should already
                // have configured the engine when called via the chain).
                mediapipe.configure(path)
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
            // LLAMA_CPP / MLC_LLM / ONNX_RUNTIME are wired in a later PR — surface a clear stub.
            else -> Result.failure(IllegalStateException(
                "Local inference runtime '${model.runtime}' is not wired in v0.3. " +
                    "MediaPipe GenAI is live (0.10.35 session API); " +
                    "other runtimes (llama.cpp / MLC / ONNX) are pending v0.4+. " +
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
                // Lazy-load on direct calls too (defensive — isAvailable() should already
                // have configured the engine when called via the chain). Configure failures
                // are surfaced as an emitted diagnostic line and we bail out.
                runCatching { mediapipe.configure(path) }
                    .onFailure { emit("[MediaPipe GenAI configure failed — ${it.message}]"); return@flow }
                val prompt = formatGemmaPrompt(messages)
                mediapipe.streamComplete(prompt, config).collect { emit(it) }
            }
            else -> {
                emit("[Local inference runtime '${model.runtime}' is not wired in v0.3 — MediaPipe GenAI is live (0.10.35); other runtimes pending v0.4+. Your model is downloaded but Local inference is unavailable for this runtime; falling back to Remote/Mock.]")
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
