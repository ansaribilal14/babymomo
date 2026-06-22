package com.babymomo.core.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediapipeLlmEngine — REAL on-device LLM inference via MediaPipe GenAI 0.10.35.
 *
 * ### v0.3 — real implementation
 * v0.2 shipped this engine as a compile-clean stub because the `tasks-genai:0.10.14`
 * AAR did not include the session API (`LlmInferenceSession`, `addQueryChunk`, per-session
 * options, streaming `ProgressListener`). v0.3 bumps to `tasks-genai:0.10.35`, which DOES
 * ship the full session API (verified by `javap` on `classes.jar` — see
 * `docs/architecture-decisions.md` §MediaPipe). This file now wraps the real API.
 *
 * ### Engine lifecycle
 * - [configure] creates a `LlmInference` engine bound to a model `.task` file. Idempotent
 *   for the same path; reloads if the path changes; closes the previous engine first.
 * - [release] closes the engine and forgets the path. Safe to call multiple times.
 * - [isLoaded] / [loadedPath] are observable for `LocalLlmProvider.isAvailable()`.
 *
 * ### Concurrency model
 * MediaPipe's `LlmInferenceSession` is NOT safe for concurrent use on a single
 * `LlmInference` engine. We serialize all inference (streaming + non-streaming) through
 * [inferenceMutex]. For v0.3 this is fine — the kernel is single-user, single-conversation.
 * If we ever pipeline agents in parallel we'll need to either pool engines or accept the
 * serialization.
 *
 * ### Streaming via ProgressListener
 * `LlmInferenceSession.generateResponseAsync(progressListener)` returns a Guava
 * `ListenableFuture<String>` and invokes the listener on the inference thread as tokens
 * are produced. Crucially, MediaPipe's `ProgressListener.run(partial, done)` delivers
 * **cumulative** text (the full text so far), not deltas. We convert cumulative → delta
 * by tracking `lastEmitted.length` and emitting only the suffix. We use a `Channel` to
 * bridge the listener callback (which fires on the MediaPipe inference thread) into the
 * `flow { }` builder's collecting coroutine. The future is awaited at the end via
 * `kotlinx.coroutines.future.await` (from `kotlinx-coroutines-guava`) so any exception
 * surfaces inside the flow.
 *
 * `ProgressListener` is a generic Java SAM interface
 * (`ProgressListener<OutputT> { run(OutputT, boolean) }`); Kotlin SAM conversion handles
 * it cleanly: `ProgressListener<String> { partial, done -> ... }`.
 */
@Singleton
class MediapipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var loadedPath: String? = null

    /** Path of the currently-loaded `.task` model (or `null` if no engine is loaded). */
    fun loadedPath(): String? = loadedPath

    /** `true` iff a `LlmInference` engine is loaded and ready for sessions. */
    fun isLoaded(): Boolean = llmInference != null

    /**
     * MediaPipe allows one in-flight `LlmInferenceSession` per `LlmInference` instance.
     * All `complete` / `streamComplete` calls acquire this mutex for their full duration
     * (session create → addQueryChunk → generateResponse → session close).
     */
    private val inferenceMutex = Mutex()

    /**
     * Engine-wide options. `maxTokens = 4096` matches Gemma 2B's context window; larger
     * contexts (8192) blow up RAM on mid-range phones. `maxTopK = 40` is the highest
     * `topK` we'll permit per-session (per-request `topK` is clamped to this engine cap).
     */
    private val engineMaxTokens = 4096
    private val engineMaxTopK = 40

    /**
     * Load (or reload) the model at [modelPath]. Idempotent when [modelPath] matches
     * [loadedPath] — returns immediately without re-creating the native engine. When the
     * path differs from the currently-loaded one, the previous engine is `close()`d first.
     *
     * On failure, `llmInference` is reset to `null` and the exception is re-thrown wrapped
     * in an `IllegalStateException` with a clear message (the underlying MediaPipe error
     * is attached as the cause).
     */
    suspend fun configure(modelPath: String) {
        val current = llmInference
        if (current != null && loadedPath == modelPath) return // already loaded
        // Different model (or first load): tear down any previous engine first.
        if (current != null) {
            runCatching { current.close() }
            llmInference = null
            loadedPath = null
        }
        runCatching {
            LlmInference.createFromOptions(
                context,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(engineMaxTokens)
                    .setMaxTopK(engineMaxTopK)
                    .build()
            )
        }.onSuccess { engine ->
            llmInference = engine
            loadedPath = modelPath
        }.onFailure { cause ->
            llmInference = null
            loadedPath = null
            throw IllegalStateException(
                "Failed to create MediaPipe LlmInference for model at '$modelPath' " +
                    "(see cause for MediaPipe-native error).",
                cause
            )
        }
    }

    /** Release the native engine. No-op if already released. Safe to call multiple times. */
    fun release() {
        runCatching { llmInference?.close() }
        llmInference = null
        loadedPath = null
    }

    /**
     * Non-streaming completion. Creates a one-shot `LlmInferenceSession`, adds the prompt
     * via `addQueryChunk`, calls `session.generateResponse()` (synchronous, returns the
     * full string), and closes the session.
     *
     * All session lifetime is bounded by `inferenceMutex.withLock { ... }` — concurrent
     * callers wait their turn.
     */
    suspend fun complete(prompt: String, config: LlmGenerationConfig): String =
        inferenceMutex.withLock {
            val engine = llmInference
                ?: throw IllegalStateException("MediapipeLlmEngine not configured — call configure(path) first")
            val session = LlmInferenceSession.createFromOptions(engine, sessionOptionsFromConfig(config))
            try {
                session.addQueryChunk(prompt)
                session.generateResponse()
            } catch (cause: Throwable) {
                throw IllegalStateException(
                    "MediaPipe LlmInferenceSession.generateResponse failed " +
                        "(prompt length = ${prompt.length}).",
                    cause
                )
            } finally {
                runCatching { session.close() }
            }
        }

    /**
     * Streaming completion. Returns a `Flow<String>` of token deltas. The flow acquires
     * [inferenceMutex] internally (so concurrent collects serialize against [complete]
     * and each other).
     *
     * Implementation: bridges the Java `ProgressListener<String>` callback (which fires
     * on the MediaPipe inference thread) into the flow's collecting coroutine via an
     * unbounded `Channel<String>`. The listener converts cumulative → delta text and
     * pushes each delta into the channel; the flow drains the channel with `for (...)`.
     * When the listener fires `done == true`, the channel is closed; the for loop exits
     * naturally; we then `await()` the `ListenableFuture<String>` returned by
     * `generateResponseAsync` to surface any error that occurred after the last progress
     * callback.
     *
     * Cancellation: if the flow collector cancels mid-stream, `for (delta in channel)`
     * throws `CancellationException`; the outer `finally` calls
     * `session.cancelGenerateResponseAsync()` (to stop native generation) and
     * `session.close()` (to free native resources).
     */
    fun streamComplete(prompt: String, config: LlmGenerationConfig): Flow<String> = flow {
        inferenceMutex.withLock {
            val engine = llmInference
                ?: throw IllegalStateException("MediapipeLlmEngine not configured — call configure(path) first")
            val session = LlmInferenceSession.createFromOptions(engine, sessionOptionsFromConfig(config))
            try {
                session.addQueryChunk(prompt)
                // Bridge MediaPipe's ProgressListener (Java SAM, fires on inference thread)
                // into this flow's coroutine via an unbounded channel. MediaPipe delivers
                // CUMULATIVE text in `partial` — we emit only the delta suffix.
                val channel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                // `lastEmittedLen` is accessed ONLY from the listener callback, which
                // MediaPipe invokes serially on its inference thread. No synchronization
                // needed — plain `var` is correct under single-writer access.
                var lastEmittedLen = 0
                val listener = ProgressListener<String> { partial, done ->
                    val delta = if (partial.length > lastEmittedLen) partial.substring(lastEmittedLen) else ""
                    lastEmittedLen = partial.length
                    if (delta.isNotEmpty()) channel.trySend(delta)
                    if (done) channel.close()
                }
                val future = session.generateResponseAsync(listener)
                try {
                    for (delta in channel) emit(delta)
                    // Surface any exception that occurred after the last progress callback.
                    future.await()
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    runCatching { session.cancelGenerateResponseAsync() }
                    throw ce
                }
            } catch (cause: Throwable) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                throw IllegalStateException(
                    "MediaPipe LlmInferenceSession streaming generateResponseAsync failed " +
                        "(prompt length = ${prompt.length}).",
                    cause
                )
            } finally {
                runCatching { session.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Map our [LlmGenerationConfig] to a MediaPipe `LlmInferenceSessionOptions`.
     *
     * - `topK` — clamped to [engineMaxTopK] (MediaPipe rejects topK > engine.maxTopK).
     * - `temperature` — passed through.
     * - `topP` — passed through (supported in 0.10.35's session builder).
     * - `randomSeed` — `config.seed?.toInt() ?: 0` (0 = MediaPipe's "no seed" default;
     *   non-zero values make sampling deterministic for tests / debugging).
     * - `maxTokens` — NOT per-request in MediaPipe; engine-wide (capped at 4096 in
     *   [configure]). `config.maxTokens` is silently ignored. Documented gap.
     * - `stopSequences` — NOT supported by MediaPipe. Silently ignored. Documented gap.
     */
    private fun sessionOptionsFromConfig(config: LlmGenerationConfig): LlmInferenceSession.LlmInferenceSessionOptions {
        val clampedTopK = config.topK.coerceIn(1, engineMaxTopK)
        return LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(clampedTopK)
            .setTopP(config.topP)
            .setTemperature(config.temperature)
            .setRandomSeed(config.seed?.toInt() ?: 0)
            .build()
    }
}
