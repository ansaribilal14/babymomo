package com.babymomo.core.memory

import com.babymomo.data.db.dao.MemoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface VectorIndex {
    suspend fun rebuild()
    suspend fun upsert(record: VectorRecord)
    suspend fun remove(id: String)
    suspend fun search(query: Embedding, k: Int = 30, namespace: String = "default"): List<SearchHit>
    fun size(): Int
}

data class SearchHit(
    val id: String,
    val score: Float,
    val content: String,
    val confidence: Float,
    val validFrom: Long,
    val createdAt: Long
)

/** FlatVectorIndex — brute-force cosine similarity in pure Kotlin. */
@Singleton
class FlatVectorIndex @Inject constructor(
    private val memoryDao: MemoryDao
) : VectorIndex {

    private val mutex = Mutex()
    private val records = mutableListOf<VectorRecord>()
    private val _size = MutableStateFlow(0)
    val sizeFlow: StateFlow<Int> = _size.asStateFlow()

    override suspend fun rebuild() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val active = memoryDao.activeEmbeddings("default")
            records.clear()
            for (m in active) {
                val emb = bytesToFloats(m.embedding, m.embeddingDims)
                records.add(VectorRecord(m.id, m.content, emb, m.confidence, m.validFrom, m.createdAt))
            }
            _size.value = records.size
        }
    }

    override suspend fun upsert(record: VectorRecord) = withContext(Dispatchers.Default) {
        mutex.withLock {
            records.removeAll { it.id == record.id }
            records.add(record)
            _size.value = records.size
        }
    }

    override suspend fun remove(id: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            records.removeAll { it.id == id }
            _size.value = records.size
        }
    }

    override suspend fun search(query: Embedding, k: Int, namespace: String): List<SearchHit> = withContext(Dispatchers.Default) {
        val snapshot: List<VectorRecord> = mutex.withLock { records.toList() }
        if (snapshot.isEmpty()) return@withContext emptyList()
        val scored = snapshot.map { r ->
            SearchHit(r.id, cosineSimilarity(query, r.embedding), r.content, r.confidence, r.validFrom, r.createdAt)
        }
        scored.sortedByDescending { it.score }.take(k)
    }

    override fun size(): Int = records.size

    private fun bytesToFloats(bytes: ByteArray, dims: Int): FloatArray {
        val out = FloatArray(dims)
        var i = 0; var j = 0
        while (i + 3 < bytes.size && j < dims) {
            val bits = ((bytes[i].toInt() and 0xFF)) or
                       ((bytes[i+1].toInt() and 0xFF) shl 8) or
                       ((bytes[i+2].toInt() and 0xFF) shl 16) or
                       ((bytes[i+3].toInt() and 0xFF) shl 24)
            out[j] = Float.fromBits(bits)
            i += 4; j += 1
        }
        return out
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += (a[i] * b[i]).toDouble()
            na += (a[i] * a[i]).toDouble()
            nb += (b[i] * b[i]).toDouble()
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-9) 0f else (dot / denom).toFloat()
    }
}
