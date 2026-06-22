package com.babymomo.core.memory

import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemorySource
import com.babymomo.data.db.entity.MemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FlatVectorIndex] — brute-force cosine similarity math and little-endian
 * byte decoding ([ByteArray] → [FloatArray]).
 *
 * The two private helpers `cosineSimilarity` and `bytesToFloats` are not exposed for tests
 * (we don't want to widen the public API of [FlatVectorIndex] for testability alone). Instead
 * we drive them through the public surface:
 *
 *  - `cosineSimilarity` is exercised by [FlatVectorIndex.search] — every SearchHit's `score`
 *    field is `cosineSimilarity(query, record.embedding)`. By constructing records with known
 *    embeddings and querying with known vectors, we can assert the exact cosine value the
 *    implementation produces (identical → 1.0, orthogonal → 0.0, opposite → -1.0, etc.).
 *
 *  - `bytesToFloats` is exercised by [FlatVectorIndex.rebuild] — it reads `MemoryEntity.embedding`
 *    (a ByteArray) and decodes it to a FloatArray. By pre-encoding floats little-endian
 *    (mirroring `MemoryService.floatsToBytes`), feeding them through a fake `MemoryDao`, calling
 *    `rebuild()`, and then `search()`ing with the original FloatArray, we verify a lossless
 *    round-trip.
 *
 * This is the recommended pattern per `docs/architecture-decisions.md` → "Testing strategy":
 * test the math, not the framework; prefer hand-written in-memory stubs over reflection or
 * widening the public API.
 */
class FlatVectorIndexCosineTest {

    private fun newIndex(dao: MemoryDao = NoopMemoryDao()): FlatVectorIndex = FlatVectorIndex(dao)

    // ---------------- cosine similarity (exercised via search) ----------------

    @Test
    fun `identical vectors have cosine similarity 1_0`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f, 0f, 0f)))
        val hits = idx.search(floatArrayOf(1f, 0f, 0f, 0f), k = 10)
        assertEquals(1, hits.size)
        assertEquals(1.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `orthogonal vectors have cosine similarity 0_0`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f, 0f, 0f)))
        val hits = idx.search(floatArrayOf(0f, 1f, 0f, 0f), k = 10)
        assertEquals(1, hits.size)
        assertEquals(0.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `opposite vectors have cosine similarity -1_0`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f, 0f, 0f)))
        val hits = idx.search(floatArrayOf(-1f, 0f, 0f, 0f), k = 10)
        assertEquals(1, hits.size)
        assertEquals(-1.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `empty vectors yield cosine 0_0 per the implementation guard`() = runTest {
        // Implementation: if (denom < 1e-9) return 0f — both norms are 0 for empty vectors.
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf()))
        val hits = idx.search(floatArrayOf(), k = 10)
        assertEquals(1, hits.size)
        assertEquals(0.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `different-length vectors yield cosine 0_0 per the size guard`() = runTest {
        // Implementation: if (a.size != b.size) return 0f.
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f, 0f))) // 3-d
        val hits = idx.search(floatArrayOf(1f, 0f), k = 10) // 2-d
        assertEquals(1, hits.size)
        assertEquals(0.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `non-trivial cosine — 45 degrees between unit vectors`() = runTest {
        // cos(45°) = sqrt(2)/2 ≈ 0.7071
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f)))
        val hits = idx.search(floatArrayOf(1f, 1f), k = 10) // not normalized, but cosine is scale-invariant
        assertEquals(1, hits.size)
        val expected = (1f * 1f) / (sqrt(1f * 1f) * sqrt(1f * 1f + 1f * 1f))
        assertEquals(expected, hits[0].score, 1e-6f)
    }

    @Test
    fun `search returns hits sorted by descending cosine similarity`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f)))
        idx.upsert(rec("m2", floatArrayOf(0.6f, 0.8f))) // cos with (1,0) = 0.6
        idx.upsert(rec("m3", floatArrayOf(-1f, 0f)))    // cos with (1,0) = -1
        val hits = idx.search(floatArrayOf(1f, 0f), k = 10)
        assertEquals(3, hits.size)
        assertEquals("m1", hits[0].id) // cos=1.0
        assertEquals("m2", hits[1].id) // cos=0.6
        assertEquals("m3", hits[2].id) // cos=-1.0
        assertTrue("hits must be sorted by descending score", hits[0].score >= hits[1].score)
        assertTrue("hits must be sorted by descending score", hits[1].score >= hits[2].score)
    }

    @Test
    fun `search returns empty list when index is empty`() = runTest {
        val idx = newIndex()
        val hits = idx.search(floatArrayOf(1f, 0f), k = 10)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `search honors the k limit`() = runTest {
        val idx = newIndex()
        repeat(5) { i -> idx.upsert(rec("m$i", floatArrayOf(i.toFloat() + 0.1f, 0f))) }
        val hits = idx.search(floatArrayOf(1f, 0f), k = 3)
        assertEquals(3, hits.size)
    }

    @Test
    fun `size reports the number of stored records`() = runTest {
        val idx = newIndex()
        assertEquals(0, idx.size())
        idx.upsert(rec("m1", floatArrayOf(1f, 0f)))
        assertEquals(1, idx.size())
        idx.upsert(rec("m2", floatArrayOf(0f, 1f)))
        assertEquals(2, idx.size())
    }

    @Test
    fun `upsert replaces a record with the same id`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f)))
        idx.upsert(rec("m1", floatArrayOf(0f, 1f))) // same id, different embedding
        assertEquals(1, idx.size())
        val hits = idx.search(floatArrayOf(0f, 1f), k = 10)
        assertEquals(1, hits.size)
        assertEquals("m1", hits[0].id)
        assertEquals(1.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `remove drops a record by id`() = runTest {
        val idx = newIndex()
        idx.upsert(rec("m1", floatArrayOf(1f, 0f)))
        idx.upsert(rec("m2", floatArrayOf(0f, 1f)))
        idx.remove("m1")
        assertEquals(1, idx.size())
        val hits = idx.search(floatArrayOf(1f, 0f), k = 10)
        // Only m2 remains; cos((1,0), (0,1)) = 0
        assertEquals(1, hits.size)
        assertEquals("m2", hits[0].id)
        assertEquals(0.0f, hits[0].score, 1e-6f)
    }

    // ---------------- byte-to-float decoding (exercised via rebuild) ----------------

    @Test
    fun `byte encoding of a 384-dim FloatArray is exactly 1536 bytes`() {
        // 384 floats × 4 bytes/float = 1536 bytes — the per-row embedding storage cost
        // documented on MemoryEntity.
        val floats = FloatArray(384) { it.toFloat() / 384f }
        val bytes = floatsToBytes(floats)
        assertEquals(1536, bytes.size)
    }

    @Test
    fun `rebuild decodes 384-dim bytes to floats losslessly`() = runTest {
        // Deterministic 384-dim float array (no RNG — tests must be reproducible).
        val original = FloatArray(384) { i -> ((i * 31) % 200) / 100f - 1f } // values in [-1, 1)
        val bytes = floatsToBytes(original)
        assertEquals(1536, bytes.size)

        val dao = InMemoryMemoryDao().apply {
            store.add(
                MemoryEntity(
                    id = "mem_roundtrip",
                    type = MemoryType.EPISODIC,
                    content = "round-trip test",
                    embedding = bytes,
                    embeddingDims = 384,
                    source = MemorySource.USER_STATED,
                    confidence = 0.9f,
                    createdAt = 1L,
                    validFrom = 1L
                )
            )
        }
        val idx = FlatVectorIndex(dao)
        idx.rebuild()
        assertEquals(1, idx.size())

        // Searching with the original embedding must yield cosine = 1.0 (lossless round-trip).
        val hits = idx.search(original, k = 10)
        assertEquals(1, hits.size)
        assertEquals("mem_roundtrip", hits[0].id)
        assertEquals(1.0f, hits[0].score, 1e-5f)
    }

    @Test
    fun `rebuild decodes a single float losslessly`() = runTest {
        val original = floatArrayOf(0.5f)
        val bytes = floatsToBytes(original)
        assertEquals(4, bytes.size)

        val dao = InMemoryMemoryDao().apply {
            store.add(
                MemoryEntity(
                    id = "mem_one",
                    type = MemoryType.EPISODIC,
                    content = "one float",
                    embedding = bytes,
                    embeddingDims = 1,
                    source = MemorySource.USER_STATED,
                    confidence = 1.0f,
                    createdAt = 1L,
                    validFrom = 1L
                )
            )
        }
        val idx = FlatVectorIndex(dao)
        idx.rebuild()
        val hits = idx.search(original, k = 10)
        assertEquals(1, hits.size)
        assertEquals(1.0f, hits[0].score, 1e-6f)
    }

    @Test
    fun `rebuild round-trips negative and large float values losslessly`() = runTest {
        // Include edge cases: negatives, zero, large magnitudes — all must survive LE encoding.
        val original = floatArrayOf(-1.5f, 2.25f, -3.75f, 0.0f, 1e10f, -1e10f, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_VALUE)
        val bytes = floatsToBytes(original)

        val dao = InMemoryMemoryDao().apply {
            store.add(
                MemoryEntity(
                    id = "mem_edges",
                    type = MemoryType.EPISODIC,
                    content = "edge cases",
                    embedding = bytes,
                    embeddingDims = original.size,
                    source = MemorySource.USER_STATED,
                    confidence = 0.5f,
                    createdAt = 1L,
                    validFrom = 1L
                )
            )
        }
        val idx = FlatVectorIndex(dao)
        idx.rebuild()
        val hits = idx.search(original, k = 10)
        assertEquals(1, hits.size)
        assertEquals(1.0f, hits[0].score, 1e-5f)
    }

    @Test
    fun `rebuild skips rows with active validUntil`() = runTest {
        val original = floatArrayOf(1f, 0f)
        val dao = InMemoryMemoryDao().apply {
            store.add(
                MemoryEntity(
                    id = "mem_active",
                    type = MemoryType.EPISODIC,
                    content = "active",
                    embedding = floatsToBytes(original),
                    embeddingDims = 2,
                    source = MemorySource.USER_STATED,
                    confidence = 0.9f,
                    createdAt = 1L,
                    validFrom = 1L,
                    validUntil = null
                )
            )
            store.add(
                MemoryEntity(
                    id = "mem_invalidated",
                    type = MemoryType.EPISODIC,
                    content = "invalidated",
                    embedding = floatsToBytes(original),
                    embeddingDims = 2,
                    source = MemorySource.USER_STATED,
                    confidence = 0.9f,
                    createdAt = 1L,
                    validFrom = 1L,
                    validUntil = 100L // invalidated — should be skipped by activeEmbeddings
                )
            )
        }
        val idx = FlatVectorIndex(dao)
        idx.rebuild()
        assertEquals("only the active row should be loaded", 1, idx.size())
    }

    // ---------------- helpers ----------------

    private fun rec(id: String, embedding: FloatArray, confidence: Float = 0.8f, createdAt: Long = 0L): VectorRecord =
        VectorRecord(id = id, content = "content for $id", embedding = embedding, confidence = confidence, validFrom = 0L, createdAt = createdAt)

    /** Mirrors `MemoryService.floatsToBytes` — little-endian IEEE 754. Used to encode floats for the rebuild tests. */
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

    private fun sqrt(x: Float): Float = kotlin.math.sqrt(x.toDouble()).toFloat()

    /** Minimal in-memory MemoryDao — supports the methods FlatVectorIndex.rebuild() touches. */
    private open class InMemoryMemoryDao : MemoryDao {
        val store = mutableListOf<MemoryEntity>()

        override suspend fun upsert(memory: MemoryEntity) {
            store.removeAll { it.id == memory.id }
            store.add(memory)
        }

        override suspend fun upsertAll(memories: List<MemoryEntity>) = memories.forEach { upsert(it) }
        override suspend fun update(memory: MemoryEntity) = upsert(memory)
        override suspend fun get(id: String): MemoryEntity? = store.firstOrNull { it.id == id }
        override suspend fun getAll(ids: List<String>): List<MemoryEntity> = store.filter { it.id in ids }

        override suspend fun activeMemories(namespace: String): List<MemoryEntity> =
            store.filter { it.validUntil == null && it.namespace == namespace && it.ttlHours != 0 }

        // FlatVectorIndex.rebuild() calls this — returns active rows so their embeddings get decoded.
        override suspend fun activeEmbeddings(namespace: String): List<MemoryEntity> = activeMemories(namespace)

        override fun recentActiveFlow(namespace: String, limit: Int): Flow<List<MemoryEntity>> =
            flowOf(activeMemories(namespace).sortedByDescending { it.createdAt }.take(limit))

        override fun byTypeFlow(type: MemoryType, limit: Int): Flow<List<MemoryEntity>> =
            flowOf(store.filter { it.type == type && it.validUntil == null }.sortedByDescending { it.createdAt }.take(limit))

        override suspend fun invalidate(memoryId: String, now: Long, byId: String?): Int {
            val idx = store.indexOfFirst { it.id == memoryId && it.validUntil == null }
            return if (idx >= 0) { store[idx] = store[idx].copy(validUntil = now, supersededBy = byId); 1 } else 0
        }

        override suspend fun softDelete(memoryId: String, now: Long): Int = invalidate(memoryId, now, null)

        override suspend fun searchContent(q: String, limit: Int): List<MemoryEntity> =
            store.filter { it.validUntil == null && it.content.contains(q) }.sortedByDescending { it.confidence }.take(limit)

        override fun activeCountFlow(): Flow<Int> = flowOf(store.count { it.validUntil == null })
        override fun totalCountFlow(): Flow<Int> = flowOf(store.size)

        override suspend fun purgeInvalidatedBefore(cutoff: Long): Int {
            val toRemove = store.filter { it.validUntil != null && it.validUntil < cutoff }
            store.removeAll(toRemove)
            return toRemove.size
        }

        override suspend fun deletePoisonedFromSource(source: MemorySource): Int {
            val poisoned = setOf("I don't know", "I cannot help", "As an AI", "I'm just a language model", "[INVALID]", "[ERROR]", "[UNKNOWN]")
            val toRemove = store.filter { it.content in poisoned && it.source == source }
            store.removeAll(toRemove)
            return toRemove.size
        }
    }

    /** No-op MemoryDao for tests that only exercise upsert/search (no rebuild). */
    private class NoopMemoryDao : InMemoryMemoryDao()
}
