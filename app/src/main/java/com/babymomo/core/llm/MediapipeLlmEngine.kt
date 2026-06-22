package com.babymomo.core.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediapipeLlmEngine — STUB (v0.2).
 *
 * ### Why this is a stub
 * The MediaPipe `tasks-genai` 0.10.14 artifact ships ONLY the top-level
 * [`LlmInference`] class plus its nested [`LlmInference.LlmInferenceOptions`].
 * The session-based API that this engine was originally written against —
 * `LlmInferenceSession`, `addQueryChunk`, per-session `generateResponse`,
 * streaming partial-result callbacks returning a Guava `ListenableFuture`,
 * per-session `Options` (temperature / topK / randomSeed) — **does not exist**
 * in 0.10.14. That session API was added in a later MediaPipe release.
 *
 * Agent A (parallel v0.2 merge) wrote against the newer session API by mistake;
 * the dependency rename from `genai-text-llm-inference-android` to `tasks-genai`
 * landed in `libs.versions.toml`, but the engine code was never reconciled with
 * the actual 0.10.14 surface. Verified by extracting `classes.jar` from the AAR
 * and running `javap` — only `LlmInference` + `LlmInference$LlmInferenceOptions`
 * (+ its `Builder`) are present.
 *
 * ### v0.2 behaviour
 * Rather than ship a half-rewired engine against the wrong API surface, v0.2
 * stubs this class out. Every method throws
 * `IllegalStateException("MediaPipe GenAI runtime API not yet finalized — see v0.3")`.
 * `LocalLlmProvider.isAvailable()` returns `false`, so `LlmProviderChain` skips
 * Local entirely and falls through to Remote → Mock. Direct callers of Local
 * (bypassing the chain) hit the stub exception, which is caught by `runCatching`
 * and surfaced as a clear failure.
 *
 * ### v0.3 plan
 * Either:
 *  - Upgrade `mediapipeGenai` to a version that ships the session API and
 *    restore the original engine, OR
 *  - Rewrite this engine against the 0.10.14 `LlmInference.generateResponse`
 *    (non-streaming, engine-level options) + `setResultListener` for streaming.
 *
 * See CHANGELOG `[0.2.0]` → `### Known Issues` for the full write-up.
 */
@Suppress("UNUSED_PARAMETER") // context is retained for Hilt DI + future v0.3 wiring
@Singleton
class MediapipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var loadedPath: String? = null

    /** Path of the currently-loaded model — always `null` in the v0.2 stub. */
    fun loadedPath(): String? = loadedPath

    /** Always `false` in the v0.2 stub — no model can actually be loaded. */
    fun isLoaded(): Boolean = false

    /**
     * Load (or reload) the model at [modelPath]. In v0.2 this ALWAYS throws —
     * the MediaPipe 0.10.14 API surface does not match this engine's design.
     */
    suspend fun configure(modelPath: String) {
        throw IllegalStateException(
            "MediaPipe GenAI runtime API not yet finalized — see v0.3"
        )
    }

    /** Non-streaming completion. In v0.2 this ALWAYS throws (stub). */
    suspend fun complete(prompt: String, config: LlmGenerationConfig): String {
        throw IllegalStateException(
            "MediaPipe GenAI runtime API not yet finalized — see v0.3"
        )
    }

    /**
     * Streaming completion. In v0.2 the returned Flow throws on first collect
     * (stub) — `LocalLlmProvider` never reaches this because `isAvailable()`
     * returns `false` and `configure` throws first.
     */
    fun streamComplete(prompt: String, config: LlmGenerationConfig): Flow<String> = flow<String> {
        throw IllegalStateException(
            "MediaPipe GenAI runtime API not yet finalized — see v0.3"
        )
    }.flowOn(Dispatchers.IO)

    /** Release the native engine. No-op in the v0.2 stub. Safe to call multiple times. */
    fun release() {
        loadedPath = null
    }
}
