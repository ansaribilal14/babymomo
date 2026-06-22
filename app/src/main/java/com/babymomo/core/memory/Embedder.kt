package com.babymomo.core.memory

typealias Embedding = FloatArray

data class VectorRecord(
    val id: String,
    val content: String,
    val embedding: Embedding,
    val confidence: Float,
    val validFrom: Long,
    val createdAt: Long
)

interface Embedder {
    val modelName: String
    val dims: Int
    suspend fun embed(text: String): Embedding
    suspend fun embedBatch(texts: List<String>): List<Embedding>
}
