package com.babymomo.core.memory

import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.dao.MetaDao
import com.babymomo.data.db.entity.MemorySource
import com.babymomo.data.db.entity.MetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryMaintenance @Inject constructor(
    private val memoryDao: MemoryDao,
    private val metaDao: MetaDao,
    private val memoryService: MemoryService,
    private val embedderProvider: EmbedderProvider
) {
    suspend fun runStartupSweep() = withContext(Dispatchers.Default) {
        ensureMetaKeys()
        val poisoned = memoryDao.deletePoisonedFromSource(MemorySource.LLM_INFERRED)
        if (poisoned > 0) memoryService.rebuildIndex()
    }

    suspend fun runPeriodicSweep() = withContext(Dispatchers.Default) {
        ensureMetaKeys()
        val poisoned = memoryDao.deletePoisonedFromSource(MemorySource.LLM_INFERRED)
        val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val gc = memoryDao.purgeInvalidatedBefore(cutoff)
        if (poisoned + gc > 0) memoryService.rebuildIndex()
        metaDao.upsert(MetaEntity("last_maintenance", System.currentTimeMillis().toString()))
    }

    private suspend fun ensureMetaKeys() {
        // Resolve the live embedder so the persisted `embedding_model` /
        // `embedding_dims` keys reflect whatever EmbedderProvider is actually
        // routing to (real BGE ONNX when its asset is shipped, MockEmbedder
        // otherwise). This drives diagnostics + future migrations.
        val activeModel = embedderProvider.modelName()
        val activeDims = embedderProvider.dims().toString()
        val defaults = listOf(
            "schema_version" to "1",
            "embedding_model" to activeModel,
            "embedding_dims" to activeDims,
            "extraction_model" to "none",
            "created_at" to System.currentTimeMillis().toString()
        )
        for ((k, v) in defaults) {
            if (metaDao.get(k) == null) metaDao.upsert(MetaEntity(k, v))
        }
    }
}
