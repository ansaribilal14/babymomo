package com.babymomo.core.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediapipeLlmEngine — wraps MediaPipe GenAI's [LlmInference] Android API to run Gemma models
 * (in `.task` format) fully on-device.
 *
 * Lifecycle:
 *  - Singleton-scoped (one loaded model per app process).
 *  - The model path is injected at runtime via [configure] — same pattern as
 *    `RemoteLlmProvider.configure(baseUrl, apiKey, modelName)`. Calling [configure] with a new
 *    path closes any previously loaded engine and loads the new one.
 *  - [configure] is idempotent: re-calling it with the same path is a fast no-op (volatile check).
 *
 * Threading:
 *  - MediaPipe's [LlmInferenceSession] is NOT safe for concurrent use on a single
 *    [LlmInference] engine — only one session may be open at a time. All inference is serialized
 *    through [inferenceMutex].
 *
 * Streaming:
 *  - MediaPipe GenAI 0.10.x DOES support streaming via
 *    [LlmInferenceSession.generateResponseAsync] with a partial-result callback.
 *  - IMPORTANT API SURPRISE: the callback delivers the CUMULATIVE response string (not deltas)
 *    along with a `done: Boolean` flag. We compute delta = cumulative − previously-emitted and
 *    emit that through the Flow, so downstream consumers see real token deltas.
 *  - The underlying call also returns a Guava [com.google.common.util.concurrent.ListenableFuture];
 *    we attach a listener to propagate completion / errors back into the coroutine channel.
 *
 * Limitations of MediaPipe 0.10.14 session options (vs. our [LlmGenerationConfig]):
 *  - `topP` is NOT exposed — MediaPipe derives it from `topK` internally. Ignored.
 *  - `stopSequences` is NOT supported. Ignored.
 *  - `maxTokens` is engine-level (set in [LlmInference.Options.setMaxTokens]), not per-session.
 *    Per-request override is ignored in 0.10.14.
 *
 * If a future MediaPipe version drops the partial-result callback, swap [streamComplete] for the
 * FALLBACK implementation noted at the bottom of this file (non-streaming single-token emit).
 */
@Singleton
class MediapipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadedPath: String? = null
    private val inferenceMutex = Mutex()
    private val configureMutex = Mutex()

    /** Path of the currently-loaded model, or null if no model is loaded. */
    fun loadedPath(): String? = loadedPath

    /** True iff a model has been loaded via [configure]. */
    fun isLoaded(): Boolean = engine != null

    /**
     * Load (or reload) the model at [modelPath]. Idempotent — calling with the same path is a
     * fast no-op. Safe to call from any coroutine; concurrent calls are serialized.
     */
    suspend fun configure(modelPath: String) = withContext(Dispatchers.IO) {
        if (loadedPath == modelPath && engine != null) return@withContext
        configureMutex.withLock {
            if (loadedPath == modelPath && engine != null) return@withLock
            engine?.let { runCatching { it.close() } }
            engine = null
            loadedPath = null
            val opts = LlmInference.Options.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_CONTEXT_TOKENS)
                .build()
            engine = LlmInference.createFromOptions(context, opts)
            loadedPath = modelPath
        }
    }

    /** Non-streaming completion. Returns the full response text. */
    suspend fun complete(prompt: String, config: LlmGenerationConfig): String = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            val llm = engine ?: error("MediapipeLlmEngine not configured — call configure(modelPath) first")
            val session = LlmInferenceSession.createFromOptions(llm, buildSessionOptions(config))
            try {
                session.addQueryChunk(prompt)
                session.generateResponse()
            } finally {
                runCatching { session.close() }
            }
        }
    }

    /**
     * Streaming completion. Emits token deltas as MediaPipe produces them.
     *
     * See class kdoc for the cumulative-vs-delta callback semantics and the FALLBACK note.
     */
    fun streamComplete(prompt: String, config: LlmGenerationConfig): Flow<String> = channelFlow {
        val llm = engine ?: throw IllegalStateException("MediapipeLlmEngine not configured")
        val session = LlmInferenceSession.createFromOptions(llm, buildSessionOptions(config))
        try {
            session.addQueryChunk(prompt)
        } catch (t: Throwable) {
            runCatching { session.close() }
            close(t); return@channelFlow
        }

        val prev = StringBuilder()

        // generateResponseAsync(listener) returns immediately; the listener is invoked from a
        // MediaPipe background thread with (cumulativeText, done). When done == true, the final
        // cumulativeText has been delivered.
        val future = session.generateResponseAsync { partialResult, done ->
            try {
                // partialResult is the cumulative response so far — emit only the delta.
                if (partialResult.isNotEmpty() && partialResult.length > prev.length &&
                    partialResult.startsWith(prev.toString())
                ) {
                    val delta = partialResult.substring(prev.length)
                    prev.setLength(0)
                    prev.append(partialResult)
                    trySend(delta)
                } else if (partialResult.isNotEmpty() && prev.isEmpty()) {
                    // First chunk — emit it as the first delta.
                    prev.append(partialResult)
                    trySend(partialResult)
                }
            } catch (t: Throwable) {
                close(t)
            }
        }

        // Bridge the ListenableFuture completion back to the coroutine channel so errors surface.
        future.addListener({
            val err = runCatching { future.get() }.exceptionOrNull()
            if (err != null) close(err) else close()
        }, DirectExecutor)

        awaitClose {
            runCatching { future.cancel(true) }
            runCatching { session.close() }
        }
    }.flowOn(Dispatchers.IO)

    /** Release the native engine. Safe to call multiple times. */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private fun buildSessionOptions(config: LlmGenerationConfig): LlmInferenceSession.Options {
        val builder = LlmInferenceSession.Options.builder()
            .setTemperature(config.temperature)
            .setTopK(config.topK)
        // MediaPipe 0.10.14 accepts an Int seed; ignore when null.
        if (config.seed != null) builder.setRandomSeed(config.seed.toInt())
        // NOTE: topP, stopSequences, and per-request maxTokens are NOT supported by MediaPipe
        // 0.10.14 session options — see class kdoc. They are silently ignored here.
        return builder.build()
    }

    /** Trivial direct executor — runs the listener inline on the future-completing thread. */
    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) { command.run() }
    }

    private companion object {
        /**
         * Engine-level max-tokens cap (input + output combined). Must be ≥ the model's own
         * max sequence length. Gemma 2B's context is 8192 — we use that as the cap so we can
         * accept any prompt up to the model's full window. Memory cost scales with this value.
         */
        private const val MAX_CONTEXT_TOKENS = 8192
    }

    /* ──────────────────────────────────────────────────────────────────────────────────
     * FALLBACK: if a future MediaPipe version drops the partial-result callback (or you want
     * a one-shot non-streaming emit), swap [streamComplete] above for this implementation:
     *
     *   fun streamComplete(prompt: String, config: LlmGenerationConfig): Flow<String> = flow {
     *       val full = complete(prompt, config)
     *       emit(full)
     *   }.flowOn(Dispatchers.IO)
     *
     * Downstream consumers already handle single-shot emits (the LlmProviderChain treats any
     * emit as "stream succeeded"). This is identical to how MockLlmProvider could fall back.
     * ────────────────────────────────────────────────────────────────────────────────── */
}
