package com.babymomo.core.memory

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** MockEmbedder — deterministic hash-based embeddings for tests + first launch. */
@Singleton
class MockEmbedder @Inject constructor() : Embedder {
    override val modelName: String = "mock-hash-384"
    override val dims: Int = 384

    override suspend fun embed(text: String): Embedding {
        val out = FloatArray(dims)
        if (text.isBlank()) return out
        val tokens = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val md = MessageDigest.getInstance("SHA-256")
        val ngrams = mutableListOf<String>()
        for (i in tokens.indices) {
            ngrams.add(tokens[i])
            if (i + 1 < tokens.size) ngrams.add(tokens[i] + "_" + tokens[i + 1])
        }
        for (ng in ngrams) {
            val hash = md.digest(ng.toByteArray())
            for (i in 0 until dims) {
                out[i] += (hash[i % hash.size].toInt() / 127.0).toFloat()
            }
        }
        return normalize(out)
    }

    override suspend fun embedBatch(texts: List<String>): List<Embedding> = texts.map { embed(it) }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (f in v) sum += (f * f).toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-9f) return v
        for (i in v.indices) v[i] /= norm
        return v
    }
}
