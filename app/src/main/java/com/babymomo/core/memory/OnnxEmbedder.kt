package com.babymomo.core.memory

import android.content.Context
import com.babymomo.data.db.dao.MetaDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** OnnxEmbedder — production embedding via ONNX Runtime Mobile + BGE-small-en-v1.5 (v0.2). */
@Singleton
class OnnxEmbedder @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val metaDao: MetaDao
) : Embedder {
    override val modelName: String = "bge-small-en-v1.5"
    override val dims: Int = 384

    override suspend fun embed(text: String): Embedding {
        throw IllegalStateException("OnnxEmbedder not yet wired — use EmbedderProvider")
    }
    override suspend fun embedBatch(texts: List<String>): List<Embedding> = texts.map { embed(it) }
}
