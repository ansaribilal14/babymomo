package com.babymomo.core.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRecaller @Inject constructor(
    private val embedderProvider: EmbedderProvider,
    private val vectorIndex: VectorIndex,
    private val memoryGraph: MemoryGraph,
    private val memoryService: MemoryService
) {
    data class RecallResult(val memories: List<RecalledMemory>, val graphFacts: List<String>)
    data class RecalledMemory(val id: String, val content: String, val confidence: Float, val source: String, val score: Float)

    suspend fun recall(query: String, topK: Int = 8): RecallResult {
        if (query.isBlank()) return RecallResult(emptyList(), emptyList())
        val qEmb = embedderProvider.current().embed(query)
        val hits = vectorIndex.search(qEmb, k = 30)
        if (hits.isEmpty()) return RecallResult(emptyList(), emptyList())

        val matchedEntities = memoryGraph.searchEntities(query.take(80), limit = 10)
        val expansion = if (matchedEntities.isNotEmpty()) memoryGraph.twoHopNeighbors(matchedEntities.map { it.id })
                        else MemoryGraph.GraphExpansion(emptyList(), emptyList())
        val entityMemoryIds = if (matchedEntities.isNotEmpty()) memoryGraph.memoryIdsForEntities(matchedEntities.map { it.id }).toSet()
                              else emptySet()

        val now = System.currentTimeMillis()
        val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        val reranked = hits.map { hit ->
            val graphProx = if (entityMemoryIds.contains(hit.id)) 1.0f else 0.0f
            val recencyDecay = kotlin.math.exp(-(now - hit.createdAt).toDouble() / THIRTY_DAYS_MS).toFloat()
            val score = 0.5f * hit.score + 0.2f * graphProx + 0.2f * hit.confidence + 0.1f * recencyDecay
            RerankCandidate(hit, score)
        }.sortedByDescending { it.score }.take(topK)

        val topMemories = reranked.mapNotNull { c ->
            val m = memoryService.get(c.hit.id) ?: return@mapNotNull null
            RecalledMemory(m.id, m.content, m.confidence, m.source.name, c.score)
        }

        val facts = expansion.relations.take(20).map { rel ->
            val src = expansion.entities.firstOrNull { it.id == rel.sourceEntityId }?.name ?: "?"
            val tgt = expansion.entities.firstOrNull { it.id == rel.targetEntityId }?.name ?: "?"
            val valid = if (rel.validUntil == null) "current"
                        else "ended ${java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date(rel.validUntil))}"
            "$src ${rel.type.name.lowercase().replace('_', ' ')} $tgt ($valid, conf=${rel.confidence})"
        }
        return RecallResult(memories = topMemories, graphFacts = facts)
    }

    private data class RerankCandidate(val hit: SearchHit, val score: Float)
}
