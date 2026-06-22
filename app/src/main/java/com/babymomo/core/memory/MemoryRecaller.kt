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
        val reranked = hits.map { hit ->
            val graphProx = if (entityMemoryIds.contains(hit.id)) 1.0f else 0.0f
            val score = computeRerankScore(
                cosineSimilarity = hit.score,
                graphProximity = graphProx,
                confidence = hit.confidence,
                ageInMillis = now - hit.createdAt,
                now = now
            )
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

    internal companion object {
        /** 30 days in milliseconds — recency-decay time constant for the reranker. */
        internal const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * 4-signal rerank score: 0.5·cosine + 0.2·graph_proximity + 0.2·confidence + 0.1·recency_decay.
         *
         * Recency decays as `exp(-age / 30 days)` — a memory 30 days old gets ~37% of the recency
         * weight, 60 days old ~13%, 90 days old ~5%. The cosine similarity dominates (weight 0.5)
         * because it's the strongest signal of topical relevance; graph proximity (0.2) rewards
         * memories tied to entities mentioned in the query; confidence (0.2) rewards extraction
         * quality; recency (0.1) is a gentle tie-breaker that prefers fresher facts.
         *
         * Extracted as `internal` so unit tests can verify the math without spinning up the full
         * recall pipeline (embedder + vector index + graph + memory service). The [now] parameter
         * is accepted explicitly so tests can pin the time base.
         */
        internal fun computeRerankScore(
            cosineSimilarity: Float,
            graphProximity: Float,
            confidence: Float,
            ageInMillis: Long,
            now: Long = System.currentTimeMillis()
        ): Float {
            val recencyDecay = kotlin.math.exp(-(now - (now - ageInMillis)).toDouble() / THIRTY_DAYS_MS).toFloat()
            return 0.5f * cosineSimilarity + 0.2f * graphProximity + 0.2f * confidence + 0.1f * recencyDecay
        }
    }
}
