package com.babymomo.app.core.memory

import com.babymomo.app.data.db.dao.MemoryDao
import com.babymomo.app.data.db.entities.MemoryEntity
import com.babymomo.app.core.llm.WrappedLlmProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryService @Inject constructor(
    private val memoryDao: MemoryDao,
    private val memoryExtractor: MemoryExtractor,
    private val memoryGraph: MemoryGraph,
    private val memoryPromoter: MemoryPromoter,
    private val vectorIndex: VectorIndex,
    private val memoryRecaller: MemoryRecaller
) {
    suspend fun processConversationTurn(userMessage: String, assistantResponse: String) {
        // Extract memories from the conversation
        val extraction = memoryExtractor.extract(userMessage, assistantResponse)

        // Store extracted entities in knowledge graph
        for (entity in extraction.entities) {
            memoryGraph.findOrCreateEntity(entity.name, entity.type)
        }

        // Store extracted relations
        for (rel in extraction.relations) {
            val fromEntity = memoryGraph.findOrCreateEntity(rel.from, "CONCEPT")
            val toEntity = memoryGraph.findOrCreateEntity(rel.to, "CONCEPT")
            memoryGraph.addRelation(fromEntity.id, toEntity.id, rel.type)
        }

        // Store extracted memories
        for (mem in extraction.memories) {
            val memoryEntity = storeMemory(mem.content, mem.type, mem.confidence)
            // Index for vector search
            vectorIndex.index(memoryEntity.id, mem.content)
            // Check if it should be promoted immediately
            memoryPromoter.checkAndPromote(memoryEntity.id)
        }
    }

    private suspend fun storeMemory(
        content: String,
        type: String,
        confidence: Double
    ): MemoryEntity {
        val memory = MemoryEntity(
            id = "mem_${System.currentTimeMillis()}_${content.hashCode()}",
            content = content,
            type = type,
            confidence = confidence,
            hitCount = 0,
            isInSystemPrompt = false,
            validFrom = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        memoryDao.insert(memory)
        return memory
    }

    suspend fun recall(query: String, topK: Int = 8): List<MemoryEntity> {
        return memoryRecaller.recall(query, topK)
    }

    suspend fun deleteMemory(id: String) {
        memoryDao.delete(id)
    }

    suspend fun getStats(): MemoryStats {
        return MemoryStats(
            activeCount = memoryDao.getActiveCount(),
            totalCount = memoryDao.getTotalCount(),
            promotedCount = memoryDao.getPromotedCount()
        )
    }
}

data class MemoryStats(
    val activeCount: Int,
    val totalCount: Int,
    val promotedCount: Int
)
