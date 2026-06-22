package com.babymomo.core.memory

import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemorySource
import com.babymomo.data.db.entity.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryService @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embedderProvider: EmbedderProvider,
    private val vectorIndex: VectorIndex
) {
    suspend fun addEpisodicMemory(content: String, source: MemorySource = MemorySource.USER_STATED, confidence: Float = 0.95f, validFrom: Long = System.currentTimeMillis(), tags: List<String> = emptyList()): MemoryEntity = withContext(Dispatchers.Default) {
        val embedding = embedderProvider.current().embed(content)
        val now = System.currentTimeMillis()
        val mem = MemoryEntity(
            id = "mem_" + UUID.randomUUID().toString().take(16),
            type = MemoryType.EPISODIC, content = content,
            embedding = floatsToBytes(embedding), embeddingDims = embedding.size,
            source = source, confidence = confidence, namespace = "default",
            createdAt = now, validFrom = validFrom, validUntil = null,
            supersededBy = null, ttlHours = -1, tags = tags.joinToString(",")
        )
        memoryDao.upsert(mem)
        vectorIndex.upsert(VectorRecord(mem.id, mem.content, embedding, mem.confidence, mem.validFrom, mem.createdAt))
        mem
    }

    suspend fun addSemanticMemory(content: String, confidence: Float, source: MemorySource = MemorySource.LLM_INFERRED, sourceMemoryId: String? = null, linkedEntities: List<com.babymomo.data.db.entity.EntityEntity> = emptyList()): MemoryEntity = withContext(Dispatchers.Default) {
        val embedding = embedderProvider.current().embed(content)
        val now = System.currentTimeMillis()
        val mem = MemoryEntity(
            id = "mem_" + UUID.randomUUID().toString().take(16),
            type = MemoryType.SEMANTIC, content = content,
            embedding = floatsToBytes(embedding), embeddingDims = embedding.size,
            source = source, confidence = confidence, namespace = "default",
            createdAt = now, validFrom = now, validUntil = null,
            supersededBy = null, ttlHours = -1, sourceMemoryId = sourceMemoryId,
            tags = linkedEntities.joinToString(",") { it.canonicalName }
        )
        memoryDao.upsert(mem)
        vectorIndex.upsert(VectorRecord(mem.id, mem.content, embedding, mem.confidence, mem.validFrom, mem.createdAt))
        mem
    }

    suspend fun addProceduralMemory(content: String, confidence: Float = 0.9f, source: MemorySource = MemorySource.USER_STATED, tags: List<String> = emptyList()): MemoryEntity = withContext(Dispatchers.Default) {
        val embedding = embedderProvider.current().embed(content)
        val now = System.currentTimeMillis()
        val mem = MemoryEntity(
            id = "mem_" + UUID.randomUUID().toString().take(16),
            type = MemoryType.PROCEDURAL, content = content,
            embedding = floatsToBytes(embedding), embeddingDims = embedding.size,
            source = source, confidence = confidence, namespace = "default",
            createdAt = now, validFrom = now, ttlHours = -1, tags = tags.joinToString(",")
        )
        memoryDao.upsert(mem)
        vectorIndex.upsert(VectorRecord(mem.id, mem.content, embedding, mem.confidence, mem.validFrom, mem.createdAt))
        mem
    }

    suspend fun invalidate(memoryId: String, byId: String? = null) = withContext(Dispatchers.Default) {
        memoryDao.invalidate(memoryId, System.currentTimeMillis(), byId)
        vectorIndex.remove(memoryId)
    }

    suspend fun get(memoryId: String): MemoryEntity? = memoryDao.get(memoryId)
    suspend fun searchContent(query: String, limit: Int = 50): List<MemoryEntity> = memoryDao.searchContent(query, limit)
    suspend fun rebuildIndex() = vectorIndex.rebuild()

    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val out = ByteArray(floats.size * 4)
        var i = 0
        for (f in floats) {
            val bits = f.toRawBits()
            out[i] = (bits and 0xFF).toByte()
            out[i + 1] = ((bits shr 8) and 0xFF).toByte()
            out[i + 2] = ((bits shr 16) and 0xFF).toByte()
            out[i + 3] = ((bits shr 24) and 0xFF).toByte()
            i += 4
        }
        return out
    }
}
