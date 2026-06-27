# 03 — Memory System

## Module Overview

The Memory System is what makes Babymomo fundamentally different from a chatbot. It implements a **bi-temporal memory graph** that remembers everything important across all conversations, grows smarter over time through a promotion mechanism, and surfaces the right memories at the right time using a 4-signal reranker. The system operates on every conversation turn: extracting entities, relations, and facts; persisting them with vector embeddings; recalling relevant memories for context; and promoting frequently-accessed memories into permanent system prompt context.

**Key Principle:** Memory extraction never blocks the user-facing response. It runs as a fire-and-forget after each turn completes.

---

## 1. Memory Types

| Type | Purpose | Examples | Retention | Color |
|------|---------|----------|-----------|-------|
| **WORKING** | Current session context, short-lived | "User is currently asking about Tokyo weather" | Auto-expires after 24h | Amber `#FFB74D` |
| **EPISODIC** | Specific events / conversations | "On June 3, user mentioned sister's wedding" | Long-term, decays slowly | Blue `#64B5F6` |
| **SEMANTIC** | Facts and knowledge about the user | "User is a software engineer", "User prefers dark mode" | Long-term, high confidence | Teal `#00E5CC` |
| **PROCEDURAL** | How-to knowledge and preferences | "User prefers concise bullet-point answers" | Long-term, very high confidence | Purple `#7C3AED` |

### Type Decision Logic

```kotlin
// MemoryExtractor assigns type based on content characteristics:
// WORKING   → "currently", "right now", "this session"
// EPISODIC  → specific events with temporal markers ("yesterday", "last week", "on June 3")
// SEMANTIC  → enduring facts ("is a", "works at", "lives in", "prefers")
// PROCEDURAL → how-to / preference patterns ("always does X", "wants Y format")
```

---

## 2. Bi-Temporal Model

Every memory and relation has two time dimensions:

| Column | Meaning |
|--------|---------|
| `validFrom` | When this fact became true (transaction time — when we recorded it) |
| `validTo` | When this fact stopped being true. `NULL` = currently valid |
| `createdAt` | When the row was inserted (system time) |

### Temporal Queries

```sql
-- Currently valid memories
SELECT * FROM memories WHERE validTo IS NULL;

-- Memories valid on a specific date
SELECT * FROM memories
WHERE validFrom <= :timestamp
  AND (validTo IS NULL OR validTo > :timestamp);

-- Expire a memory (set validTo)
UPDATE memories SET validTo = :now WHERE id = :id;
```

### Use Cases

| Scenario | Behavior |
|----------|----------|
| User changes job | Old SEMANTIC memory gets `validTo=now`, new SEMANTIC memory created with `validFrom=now` |
| Working memory expires | MemoryMaintenanceWorker sets `validTo` on WORKING memories older than 24h |
| Memory promoted | `validTo=now` set (no longer in volatile recall pool), `isInSystemPrompt=true` |

---

## 3. 4-Signal Reranker

The `MemoryRecaller` scores every active memory using four weighted signals and returns the top-K results.

### Formula

```
final_score(memory) =
    (0.40 × cosine_similarity(query_embedding, memory_embedding))
  + (0.30 × graph_proximity(query_entities, memory_entities))
  + (0.20 × memory.confidence)
  + (0.10 × recency_decay(memory.createdAt))
```

### Signal Details

#### Signal 1: Cosine Similarity (weight: 0.40)

```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f; var normA = 0f; var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
}
```

#### Signal 2: Graph Proximity (weight: 0.30)

```
graph_proximity = min(1.0, Σ (1 / hop_distance(query_entity, memory_entity)))
```

If query entities and memory entities share direct graph connections, proximity = 1.0. If they're connected through 2 hops, proximity = 0.5. No connection = 0.0.

#### Signal 3: Confidence (weight: 0.20)

The `confidence` field (0.0–1.0) set during extraction. Higher confidence memories rank higher.

#### Signal 4: Recency Decay (weight: 0.10)

```
recency_decay = e^(-λ × age_days)    where λ = 0.01/day
```

A memory from today: score ≈ 1.0. A memory from 30 days ago: score ≈ 0.74. A memory from 100 days ago: score ≈ 0.37.

### Implementation

```kotlin
@Singleton
class MemoryRecaller @Inject constructor(
    private val memoryDao: MemoryDao,
    private val vectorIndex: VectorIndex,
    private val entityDao: EntityDao,
    private val relationDao: RelationDao
) {
    suspend fun recall(query: String, topK: Int = 8): List<MemoryEntity> {
        // Step 1: Get vector similarity candidates
        val vectorResults = vectorIndex.search(query, topK * 2)
        val vectorScores = vectorResults.associate { it.first to it.second }

        // Step 2: Get text-matched memories as candidates
        val textMatches = memoryDao.search(query)

        // Step 3: Merge candidates (union of vector + text matches)
        val candidates = (vectorResults.map { it.first } + textMatches.map { it.id }).toSet()

        // Step 4: Score each candidate using 4-signal reranker
        val scored = candidates.mapNotNull { memoryId ->
            val memory = memoryDao.getById(memoryId) ?: return@mapNotNull null
            val cosineScore = vectorScores[memoryId] ?: 0.4
            val graphScore = calculateGraphProximity(query, memory)
            val confidenceScore = memory.confidence
            val recencyScore = calculateRecencyDecay(memory.createdAt)

            val finalScore = (0.40 * cosineScore) +
                    (0.30 * graphScore) +
                    (0.20 * confidenceScore) +
                    (0.10 * recencyScore)

            memory to finalScore
        }.sortedByDescending { it.second }.take(topK)

        // Step 5: Increment hit counts for recalled memories
        scored.forEach { (memory, _) ->
            memoryDao.incrementHitCount(memory.id)
        }

        return scored.map { it.first }
    }

    suspend fun getPromotedMemories(): List<MemoryEntity> {
        return memoryDao.getPromoted()
    }

    private suspend fun calculateGraphProximity(query: String, memory: MemoryEntity): Double {
        val queryEntities = entityDao.search(query)
        if (queryEntities.isEmpty()) return 0.0

        var proximity = 0.0
        for (entity in queryEntities) {
            val relations = relationDao.getByEntity(entity.id)
            // Direct connections score 1.0/hop
            for (rel in relations) {
                val connectedId = if (rel.fromEntityId == entity.id) rel.toEntityId else rel.fromEntityId
                proximity += 1.0 / rel.weight.coerceAtLeast(1.0)
            }
        }
        return minOf(1.0, proximity / queryEntities.size)
    }

    private fun calculateRecencyDecay(createdAt: Long): Double {
        val ageMs = System.currentTimeMillis() - createdAt
        val ageDays = ageMs / (1000.0 * 60 * 60 * 24)
        val lambda = 0.01  // decay constant per day
        return exp(-lambda * ageDays)
    }
}
```

---

## 4. MemoryExtractor

### Extraction Prompt

After every AI response, `MemoryExtractor` runs a separate LLM call with this prompt:

```
You are a memory extraction agent. From the conversation below, extract:
1. ENTITIES: named people, places, projects, or concepts mentioned
2. RELATIONS: how entities relate to each other
3. FACTS: specific facts about the user worth remembering long-term
4. TYPE: classify each fact as WORKING / EPISODIC / SEMANTIC / PROCEDURAL

User: {userMessage}
Assistant: {assistantResponse}

Respond ONLY with JSON. No prose. Schema:
{
  "entities": [{"name": "", "type": "PERSON|PLACE|CONCEPT|PROJECT|THING", "description": ""}],
  "relations": [{"from": "", "to": "", "type": ""}],
  "memories": [{"content": "", "type": "WORKING|EPISODIC|SEMANTIC|PROCEDURAL", "confidence": 0.9}]
}
```

### Extraction Result Data Classes

```kotlin
data class ExtractionResult(
    val entities: List<ExtractionEntity>,
    val relations: List<ExtractionRelation>,
    val memories: List<ExtractionMemory>
)

data class ExtractionEntity(val name: String, val type: String, val description: String?)
data class ExtractionRelation(val from: String, val to: String, val type: String)
data class ExtractionMemory(val content: String, val type: String, val confidence: Double)
```

### Error Handling

- If the LLM returns malformed JSON: return `ExtractionResult(emptyList(), emptyList(), emptyList())`
- If the LLM call fails entirely: return empty result
- If JSON contains unexpected fields: `ignoreUnknownKeys = true` in Json config
- If markdown code fences wrap the JSON: strip ` ```json ` and ` ``` ` before parsing

---

## 5. MemoryPromoter

### Promotion Rule

```
On every memory hitCount increment:
  if memory.hitCount >= 5 AND memory.isInSystemPrompt == false:
    memory.isInSystemPrompt = true
    memory.validTo = now()     // mark as no longer in volatile store
    → included in [PROMOTED MEMORIES] section of every future call
```

### Implementation

```kotlin
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
```

### Promotion Lifecycle

```
New memory: hitCount=0, isInSystemPrompt=false, validTo=null
    │
    │ (recalled in 5 separate turns)
    ▼
hitCount=5 → checkAndPromote()
    │
    ▼
isInSystemPrompt=true, validTo=now()
    │
    ▼
Now appears in [PROMOTED MEMORIES — PERMANENT] section of every system prompt
No longer appears in volatile recall results (validTo != null)
```

---

## 6. VectorIndex

### Design

In v1.0, VectorIndex uses **flat cosine search** — brute-force comparison of the query embedding against all stored embeddings. This is simple and correct, suitable for up to ~10K memories. In v1.1, this will be replaced with HNSW for better scaling.

### Embedding Providers

| Version | Provider | Dimension | Method |
|---------|----------|-----------|--------|
| v1.0 | MockEmbedder | 384 | Deterministic hash-based |
| v1.1 | OnnxEmbedder | 384 | BGE-small-en-v1.5 ONNX model |

### MockEmbedder Implementation

```kotlin
@Singleton
class MockEmbedder {
    fun embed(text: String): FloatArray {
        val dim = 384
        val result = FloatArray(dim)
        val hash = text.hashCode()
        val rng = java.util.Random(hash.toLong())
        for (i in 0 until dim) {
            result[i] = (rng.nextFloat() * 2 - 1).toFloat()
        }
        // Normalize to unit vector
        val norm = sqrt(result.sumOf { (it * it).toDouble() }).toFloat()
        for (i in result.indices) {
            result[i] /= norm
        }
        return result
    }
}
```

### VectorIndex Implementation

```kotlin
@Singleton
class VectorIndex @Inject constructor(
    private val memoryVectorDao: MemoryVectorDao,
    private val embedder: MockEmbedder
) {
    private val cache = mutableListOf<Pair<String, FloatArray>>()

    suspend fun index(memoryId: String, content: String) {
        val embedding = embedder.embed(content)
        val entity = MemoryVectorEntity(
            id = "vec_$memoryId",
            memoryId = memoryId,
            embedding = embedding.toByteArray(),
            dimension = 384
        )
        memoryVectorDao.insert(entity)
        cache.add(memoryId to embedding)
    }

    suspend fun search(query: String, topK: Int): List<Pair<String, Double>> {
        val queryEmbedding = embedder.embed(query)
        loadCacheIfEmpty()

        return cache.map { (id, emb) ->
            val similarity = cosineSimilarity(queryEmbedding, emb)
            id to similarity.toDouble()
        }.sortedByDescending { it.second }.take(topK)
    }

    private suspend fun loadCacheIfEmpty() {
        if (cache.isEmpty()) {
            val allVectors = memoryVectorDao.getAll()
            cache.clear()
            cache.addAll(allVectors.map { it.memoryId to it.embedding.toFloatArray() })
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
    }
}
```

---

## 7. MemoryGraph

### Knowledge Graph Operations

```kotlin
@Singleton
class MemoryGraph @Inject constructor(
    private val entityDao: EntityDao,
    private val relationDao: RelationDao
) {
    suspend fun findOrCreateEntity(name: String, type: String): EntityEntity {
        val existing = entityDao.search(name).firstOrNull()
        return existing ?: EntityEntity(
            id = "ent_${System.currentTimeMillis()}_${name.hashCode()}",
            name = name,
            type = type,
            createdAt = System.currentTimeMillis()
        ).also { entityDao.insert(it) }
    }

    suspend fun addRelation(fromId: String, toId: String, type: String) {
        RelationEntity(
            id = "rel_${System.currentTimeMillis()}_${type.hashCode()}",
            fromEntityId = fromId,
            toEntityId = toId,
            type = type,
            weight = 1.0,
            validFrom = System.currentTimeMillis()
        ).also { relationDao.insert(it) }
    }

    suspend fun getRelatedEntities(entityId: String): List<RelationEntity> {
        return relationDao.getByEntity(entityId)
    }
}
```

### Entity Types

| Type | Description | Examples |
|------|------------|---------|
| PERSON | Named people | "Sarah", "Dr. Kim" |
| PLACE | Locations | "Tokyo", "home office" |
| CONCEPT | Abstract ideas | "machine learning", "minimalism" |
| PROJECT | User projects | "Home Renovation" |
| THING | Physical objects | "Mochi" (cat), "MacBook" |

### Relation Types (LLM-generated)

The LLM generates relation types during extraction. Common patterns:

```
KNOWS, WORKS_AT, LIVES_IN, OWNS, PART_OF, RELATED_TO,
WORKS_ON, PREFERES, DISLIKES, BELONGS_TO
```

---

## 8. MemoryService — Orchestrator

### Orchestration Flow

```kotlin
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
        // 1. Extract
        val extraction = memoryExtractor.extract(userMessage, assistantResponse)

        // 2. Store entities in knowledge graph
        for (entity in extraction.entities) {
            memoryGraph.findOrCreateEntity(entity.name, entity.type)
        }

        // 3. Store relations
        for (rel in extraction.relations) {
            val fromEntity = memoryGraph.findOrCreateEntity(rel.from, "CONCEPT")
            val toEntity = memoryGraph.findOrCreateEntity(rel.to, "CONCEPT")
            memoryGraph.addRelation(fromEntity.id, toEntity.id, rel.type)
        }

        // 4. Store memories
        for (mem in extraction.memories) {
            val memoryEntity = storeMemory(mem.content, mem.type, mem.confidence)
            vectorIndex.index(memoryEntity.id, mem.content)
            memoryPromoter.checkAndPromote(memoryEntity.id)
        }
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
```

---

## 9. Data Flow Diagram

```
Conversation Turn Complete
   │
   ▼
MemoryService.processConversationTurn(userMsg, assistantResponse)
   │
   ├─► MemoryExtractor.extract(userMsg, response)
   │      │ (LLM call, fire-and-forget)
   │      ▼
   │   ExtractionResult(entities, relations, memories)
   │      │
   │      ├──► MemoryGraph.findOrCreateEntity(name, type) × N
   │      │       └──► EntityDao.insert(entity)
   │      │
   │      ├──► MemoryGraph.addRelation(from, to, type) × N
   │      │       └──► RelationDao.insert(relation)
   │      │
   │      └──► storeMemory(content, type, confidence) × N
   │              ├──► MemoryDao.insert(memory)
   │              ├──► VectorIndex.index(memoryId, content)
   │              │       ├──► MockEmbedder.embed(content) → FloatArray[384]
   │              │       └──► MemoryVectorDao.insert(vector)
   │              └──► MemoryPromoter.checkAndPromote(memoryId)
   │                      └──► (hitCount >= 5?) → MemoryDao.promote(id, now)
   │
   ▼
Next Turn: MemoryRecaller.recall(query)
   │
   ├──► VectorIndex.search(query, topK*2)
   │       ├──► MockEmbedder.embed(query) → FloatArray[384]
   │       └──► cosine similarity against all stored embeddings
   │
   ├──► MemoryDao.search(query) → text matches
   │
   ├──► Calculate 4-signal scores for all candidates
   │       ├──► cosine (from vector search): weight 0.40
   │       ├──► graph proximity (entity search + relation hops): weight 0.30
   │       ├──► confidence (from memory field): weight 0.20
   │       └──► recency decay (e^(-λt)): weight 0.10
   │
   ├──► Sort by final_score, take top-8
   │
   └──► MemoryDao.incrementHitCount(id) × 8
           └──► MemoryPromoter.checkAndPromote(id) after each increment
```

---

## 10. Error Handling

| Component | Error | Recovery |
|-----------|-------|----------|
| MemoryExtractor | LLM call fails | Return empty ExtractionResult |
| MemoryExtractor | Malformed JSON | Strip code fences, try again; if still fails, return empty |
| VectorIndex | DAO read fails | Fall back to text-only search from MemoryDao |
| MemoryGraph | Entity already exists | Find and return existing entity |
| MemoryPromoter | Memory not found by ID | Return silently |
| MemoryService | Any step fails | Skip that step, continue to next; never throw to caller |
| MockEmbedder | Empty input | Return zero vector |

---

## 11. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `extract_simpleConversation` | "My name is Alex" → extract | Entity: Alex (PERSON), Memory: name is Alex (SEMANTIC) |
| `extract_multipleEntities` | "Sarah and I work at Google" | Entities: Sarah, Google. Relations: WORKS_AT |
| `extract_emptyResult` | "Hello" | ExtractionResult with empty lists (nothing worth remembering) |
| `recall_vectorSimilarity` | Store "user loves cats", query "feline pets" | Memory recalled (high cosine) |
| `recall_graphProximity` | Entity "Sarah" linked to entity "Google", query "Sarah" | Google-related memories boosted |
| `recall_recencyDecay` | Old memory vs new memory, same type | New memory scores higher |
| `recall_topK` | 20 memories stored, recall with topK=5 | Exactly 5 memories returned |
| `promote_after5Hits` | Recall same memory 5 times | isInSystemPrompt=true |
| `promote_noDoublePromote` | Promote already-promoted memory | No error, no duplicate |
| `workingMemory_expires` | Working memory older than 24h | MemoryMaintenanceWorker sets validTo |
| `bitTemporal_queryByTime` | Query memories valid on specific date | Only valid-then memories returned |
| `vectorIndex_cosineAccuracy` | Store embedding, search with similar query | Top result has highest cosine |
| `mockEmbedder_deterministic` | Embed same text twice | Identical FloatArrays |
| `stats_correctCounts` | 10 active, 3 promoted, 15 total | Stats match DAO counts |
