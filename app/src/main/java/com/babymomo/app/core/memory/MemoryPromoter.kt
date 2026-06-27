package com.babymomo.app.core.memory

import com.babymomo.app.data.db.dao.MemoryDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryPromoter @Inject constructor(
    private val memoryDao: MemoryDao
) {
    suspend fun checkAndPromote(memoryId: String) {
        val memory = memoryDao.getById(memoryId) ?: return
        if (memory.hitCount >= PROMOTION_THRESHOLD && !memory.isInSystemPrompt) {
            memoryDao.promote(memoryId, System.currentTimeMillis())
        }
    }

    companion object {
        const val PROMOTION_THRESHOLD = 5
    }
}
