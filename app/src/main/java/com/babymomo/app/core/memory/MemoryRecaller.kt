package com.babymomo.app.core.memory

import com.babymomo.app.data.db.dao.EntityDao
import com.babymomo.app.data.db.dao.MemoryDao
import com.babymomo.app.data.db.dao.RelationDao
import com.babymomo.app.data.db.entities.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln

@Singleton
class MemoryRecaller @Inject constructor(
    private val memoryDao: MemoryDao,
    private val vectorIndex: VectorIndex,
    private val entityDao: EntityDao,
    private val relationDao: RelationDao
) {
    suspend fun recall(query: String, topK: Int = 8): List<MemoryEntity> {
        // Get vector similarity candidates
        val vectorResults = vectorIndex.search(query, topK * 2)
        val vectorScores = vectorResults.associate { it.first to it.second }

        // Get all active memories
        val allActive = memoryDao.search(query)

        // Score each memory using 4-signal reranker
        val scored = allActive.map { memory ->
            val cosineScore = vectorScores["mem_${memory.id}"] ?: 0.4f.toDouble()

            val graphScore = calculateGraphProximity(query, memory)
            val confidenceScore = memory.confidence
            val recencyScore = calculateRecencyDecay(memory.createdAt)

            val finalScore = (0.40 * cosineScore) +
                    (0.30 * graphScore) +
                    (0.20 * confidenceScore) +
                    (0.10 * recencyScore)

            memory to finalScore
        }.sortedByDescending { it.second }.take(topK)

        // Increment hit count for recalled memories
        scored.forEach { (memory, _) ->
            memoryDao.incrementHitCount(memory.id)
        }

        return scored.map { it.first }
    }

    suspend fun getPromotedMemories(): List<MemoryEntity> {
        return memoryDao.getPromoted()
    }

    private suspend fun calculateGraphProximity(query: String, memory: MemoryEntity): Double {
        // Simplified graph proximity - check if query entities overlap with memory entities
        val queryEntities = entityDao.search(query)
        if (queryEntities.isEmpty()) return 0.0

        // Check relations for proximity
        var proximity = 0.0
        for (entity in queryEntities) {
            val relations = relationDao.getByEntity(entity.id)
            if (relations.isNotEmpty()) proximity += 0.5
        }
        return minOf(1.0, proximity)
    }

    private fun calculateRecencyDecay(createdAt: Long): Double {
        val ageMs = System.currentTimeMillis() - createdAt
        val ageDays = ageMs / (1000.0 * 60 * 60 * 24)
        val lambda = 0.01  // decay constant per day
        return exp(-lambda * ageDays)
    }
}
