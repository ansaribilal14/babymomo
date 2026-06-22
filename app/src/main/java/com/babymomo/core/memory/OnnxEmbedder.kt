package com.babymomo.core.memory

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.content.Context
import com.babymomo.data.db.dao.MetaDao
import com.babymomo.data.db.entity.MetaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * OnnxEmbedder — production embeddings via ONNX Runtime Mobile + BGE-small-en-v1.5.
 *
 * Loads the int8-quantized BGE-small-en-v1.5 ONNX model from the app's
 * `assets/models/` directory on first use, then serves 384-dim L2-normalized
 * embeddings through the standard BERT input contract (`input_ids` /
 * `attention_mask` / `token_type_ids`).
 *
 * ### Tokenizer (v0.3)
 * Tokenization is handled by [BertTokenizer] — a real BERT WordPiece tokenizer
 * backed by the bundled `bert-base-uncased` 30,522-token `vocab.txt`. The model
 * now sees the same token-id distribution it was trained on, so semantic
 * quality matches the BGE-small reference numbers. See `BertTokenizer.kt`'s
 * KDoc for the full algorithm.
 *
 * ### Graceful degradation
 * As of v0.3 the real ~33 MB int8 ONNX binary is bundled as an app asset, so
 * [ensureLoaded] succeeds on first call and real 384-dim BGE embeddings are
 * produced at runtime. The fallback path is still wired, however: if the asset
 * is ever absent (someone deletes it from a custom build, asset extraction to
 * `filesDir` fails with `IOException`, the file is corrupt, or an ABI-level
 * `OrtSession` creation fails), [ensureLoaded] returns `false` and [embed]
 * throws [IllegalStateException]. [EmbedderProvider] is expected to catch this
 * and fall back to [MockEmbedder] — so app functionality never breaks when the
 * model is unavailable.
 *
 * ### Inference pipeline
 * 1. Tokenize → `LongArray[512]` × 3 tensors (input_ids / attention_mask / token_type_ids).
 * 2. Run `session.run(inputs)` → `last_hidden_state` of shape `[1, 512, 384]`.
 * 3. Mean-pool over real tokens (those with `attention_mask = 1`) — BGE convention.
 * 4. L2-normalize the 384-dim vector.
 *
 * ### Acceleration
 * [SessionOptions.addNnapi] enables the Android NNAPI delegate, which routes
 * supported ops to GPU/NPU on capable devices. The call is wrapped in
 * `runCatching` so devices without NNAPI support transparently fall back to
 * the CPU execution provider.
 */
@Singleton
class OnnxEmbedder @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val metaDao: MetaDao,
    private val tokenizer: BertTokenizer
) : Embedder {
    override val modelName: String = MODEL_NAME
    override val dims: Int = DIMS

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    @Volatile private var session: OrtSession? = null
    /** Set true once we've decided the model is unavailable, so we don't retry every call. */
    @Volatile private var unavailable: Boolean = false

    /**
     * Lazy-load the BGE-small ONNX model from app assets. Returns `true` iff a
     * session is ready for inference after this call.
     *
     * Safe to call repeatedly — short-circuits when already loaded or when a
     * previous attempt decided the model is unavailable.
     */
    suspend fun ensureLoaded(): Boolean {
        session?.let { return true }
        if (unavailable) return false

        val modelFile = extractAssetIfPresent() ?: run {
            unavailable = true
            return false
        }

        return try {
            val opts = SessionOptions().apply {
                // NNAPI delegate = GPU/NPU acceleration on supported devices.
                // Wrap in runCatching: some emulators / older API levels throw
                // on addNnapi(); CPU provider still works without it.
                runCatching { addNnapi() }
            }
            val s = env.createSession(modelFile.absolutePath, opts)
            session = s
            metaDao.upsert(MetaEntity(KEY_EMBEDDING_MODEL_READY, "true"))
            true
        } catch (t: Throwable) {
            // Corrupt model file, OOM, ABI mismatch, etc. — don't crash the app;
            // fall back to mock by marking unavailable.
            unavailable = true
            false
        }
    }

    /** Quick synchronous readiness probe — `true` iff a session is already loaded. */
    fun isReady(): Boolean = session != null

    /**
     * Copy the bundled model asset to `filesDir` on first run (so ONNX Runtime
     * can mmap it), or return the previously-extracted file. Returns `null`
     * when the asset is absent (only the `.placeholder` marker is present).
     */
    private fun extractAssetIfPresent(): File? {
        val outFile = File(ctx.filesDir, MODEL_REL_PATH).apply {
            parentFile?.mkdirs()
        }
        // Already extracted by a previous run — reuse it (lets ONNX Runtime mmap).
        if (outFile.exists() && outFile.length() > 0) return outFile
        return try {
            ctx.assets.open(MODEL_ASSET_PATH).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            outFile
        } catch (_: FileNotFoundException) {
            null // asset missing (only .placeholder shipped) → graceful mock fallback
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun embed(text: String): Embedding {
        if (!ensureLoaded()) {
            throw IllegalStateException(
                "ONNX model not available — falling back to mock"
            )
        }
        val s = session ?: throw IllegalStateException("ONNX session closed")
        val tok = tokenizer.tokenize(text)
        return runInference(s, tok)
    }

    /**
     * v0.2: simple sequential map — each text is tokenized + inferred
     * independently. v0.3 should stack inputs into a `[N, 512]` batch tensor
     * and run a single `session.run` for ~N× throughput.
     */
    override suspend fun embedBatch(texts: List<String>): List<Embedding> =
        texts.map { embed(it) }

    private fun runInference(s: OrtSession, tok: BertTokenizer.Tokenized): Embedding {
        // Shape [1, 512] tensors — wrap the 1D arrays in a single-element Array.
        val inputIdsT = OnnxTensor.createTensor(env, arrayOf(tok.inputIds))
        val attentionT = OnnxTensor.createTensor(env, arrayOf(tok.attentionMask))
        val tokenTypeT = OnnxTensor.createTensor(env, arrayOf(tok.tokenTypeIds))

        val inputs = mapOf<String, OnnxTensor>(
            INPUT_INPUT_IDS to inputIdsT,
            INPUT_ATTENTION_MASK to attentionT,
            INPUT_TOKEN_TYPE_IDS to tokenTypeT
        )

        // Number of real (non-pad) tokens — used to bound mean-pooling.
        val realLen = tok.attentionMask.sumOf { it.toInt() }

        return try {
            s.run(inputs).use { result ->
                // BGE ONNX exports `last_hidden_state` as the first (only) output,
                // shape [batch=1, seq_len=512, hidden=384]. Java-side unwrap:
                //   Array<Array<FloatArray>>  (3-D, Float element type)
                @Suppress("UNCHECKED_CAST")
                val hidden = (result.get(0).value as Array<Array<FloatArray>>)[0] // [512, 384]

                // Mean-pool over real tokens only (BGE convention).
                val pooled = FloatArray(dims)
                var n = 0
                val upper = realLen.coerceAtMost(hidden.size)
                for (i in 0 until upper) {
                    val tokenVec = hidden[i]
                    if (tokenVec.size < dims) continue // defensive: malformed row
                    for (j in 0 until dims) pooled[j] += tokenVec[j]
                    n++
                }
                if (n > 0) {
                    // Explicit division (not /=) — Kotlin 1.9.22's augmented-assignment
                    // resolution on FloatArray indexed access occasionally fails to pick the
                    // matching set(int, Float) overload when the RHS involves Int promotion.
                    // Plain assignment + toFloat() sidesteps that.
                    val invN = 1f / n
                    for (j in 0 until dims) pooled[j] = pooled[j] * invN
                }

                // BGE-small is trained with L2-normalized outputs — match here.
                normalize(pooled)
            }
        } finally {
            inputIdsT.close()
            attentionT.close()
            tokenTypeT.close()
        }
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (f in v) sum += (f * f).toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-9f) return v
        for (i in v.indices) v[i] /= norm
        return v
    }

    /** Release the native session. Safe to call multiple times. */
    fun close() {
        session?.close()
        session = null
        unavailable = false
    }

    companion object {
        private const val MODEL_NAME = "bge-small-en-v1.5"
        private const val DIMS = 384

        /** Path of the model inside the APK's `assets/` dir. */
        private const val MODEL_ASSET_PATH = "models/bge-small-en-v1.5-int8.onnx"
        /** Path of the extracted model under `context.filesDir`. */
        private const val MODEL_REL_PATH = "models/bge-small-en-v1.5-int8.onnx"

        /** Standard BERT-family ONNX input/output names. */
        private const val INPUT_INPUT_IDS = "input_ids"
        private const val INPUT_ATTENTION_MASK = "attention_mask"
        private const val INPUT_TOKEN_TYPE_IDS = "token_type_ids"

        /** MetaDao key set to "true" once a session is successfully loaded. */
        private const val KEY_EMBEDDING_MODEL_READY = "embedding_model_ready"
    }
}
