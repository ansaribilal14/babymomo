package com.babymomo.core.memory

import com.babymomo.data.db.dao.MetaDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbedderProvider @Inject constructor(
    private val mock: MockEmbedder,
    private val onnx: OnnxEmbedder,
    private val metaDao: MetaDao
) {
    suspend fun current(): Embedder = mock  // v0.2: check meta for ONNX model readiness
    suspend fun modelName(): String = current().modelName
    suspend fun dims(): Int = current().dims
}
