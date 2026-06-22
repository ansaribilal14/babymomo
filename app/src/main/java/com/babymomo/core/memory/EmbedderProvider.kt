package com.babymomo.core.memory

import com.babymomo.data.db.dao.MetaDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes to the best available [Embedder] at call time.
 *
 * v0.2 strategy: prefer [OnnxEmbedder] (real BGE-small-en-v1.5) whenever its
 * session is either already loaded or can be loaded from the bundled asset.
 * If the model asset isn't shipped (only the `.placeholder` marker exists, as
 * in dev builds) the call falls through to [MockEmbedder] transparently —
 * callers never have to handle a missing-model exception.
 *
 * `MetaDao` is held here (not currently read) so that future versions can
 * persist the user's embedder choice across launches without touching the
 * constructor surface.
 */
@Singleton
class EmbedderProvider @Inject constructor(
    private val mock: MockEmbedder,
    private val onnx: OnnxEmbedder,
    private val metaDao: MetaDao
) {
    suspend fun current(): Embedder =
        if (onnx.isReady() || onnx.ensureLoaded()) onnx else mock

    suspend fun modelName(): String = current().modelName
    suspend fun dims(): Int = current().dims
}
