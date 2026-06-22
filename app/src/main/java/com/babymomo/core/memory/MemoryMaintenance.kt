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
    private val memoryService: MemoryService
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
        val defaults = listOf(
            "schema_version" to "1",
            "embedding_model" to "mock-hash-384",
            "embedding_dims" to "384",
            "extraction_model" to "none",
            "created_at" to System.currentTimeMillis().toString()
        )
        for ((k, v) in defaults) {
            if (metaDao.get(k) == null) metaDao.upsert(MetaEntity(k, v))
        }
    }
}
