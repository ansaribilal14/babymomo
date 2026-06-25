package com.babymomo.app.core.memory

import com.babymomo.app.data.db.dao.MemoryVectorDao
import com.babymomo.app.data.db.entities.MemoryVectorEntity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class MockEmbedder @Inject constructor() {

    fun embed(text: String): FloatArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray())
        val embedding = FloatArray(384)
        for (i in embedding.indices) {
            val byteIdx = i % hash.size
            val nextByteIdx = (i + 1) % hash.size
            embedding[i] = ((hash[byteIdx].toInt() and 0xFF) / 255.0f) *
                ((hash[nextByteIdx].toInt() and 0xFF) / 255.0f) *
                (1.0f + (i % 10) * 0.01f)
        }
        return normalize(embedding)
    }

    private fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm < 1e-8f) return vec
        return FloatArray(vec.size) { vec[it] / norm }
    }
}

@Singleton
class VectorIndex @Inject constructor(
    private val memoryVectorDao: MemoryVectorDao,
    private val embedder: MockEmbedder
) {
    suspend fun index(memoryId: String, text: String) {
        val embedding = embedder.embed(text)
        val entity = MemoryVectorEntity(
            id = "vec_${memoryId}",
            memoryId = memoryId,
            embedding = floatArrayToByteArray(embedding),
            dimension = 384
        )
        memoryVectorDao.insert(entity)
    }

    suspend fun search(query: String, topK: Int = 8): List<Pair<String, Float>> {
        val queryEmbedding = embedder.embed(query)
        val allVectors = memoryVectorDao.getAll()

        return allVectors.mapNotNull { vec ->
            val embedding = byteArrayToFloatArray(vec.embedding)
            val similarity = cosineSimilarity(queryEmbedding, embedding)
            vec.memoryId to similarity
        }.sortedByDescending { it.second }.take(topK)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-8f) dot / denom else 0f
    }

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val bytes = ByteArray(floats.size * 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        floats.forEach { buffer.putFloat(it) }
        return bytes
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floats = FloatArray(bytes.size / 4)
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        for (i in floats.indices) floats[i] = buffer.getFloat()
        return floats
    }
}
