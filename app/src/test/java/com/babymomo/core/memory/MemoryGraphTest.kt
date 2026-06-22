package com.babymomo.core.memory

import com.babymomo.data.db.dao.EntityDao
import com.babymomo.data.db.dao.MemoryEntityLinkDao
import com.babymomo.data.db.dao.RelationDao
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.EntityType
import com.babymomo.data.db.entity.MemoryEntityLink
import com.babymomo.data.db.entity.RelationEntity
import com.babymomo.data.db.entity.RelationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryGraph] — entity resolution (canonicalization, alias merging),
 * relation assertion (dedup + bi-temporal invalidation), and 1-/2-hop neighbor expansion.
 *
 * Strategy: hand-written in-memory DAO implementations (no MockK dependency). The in-memory
 * DAOs faithfully mimic the SQL semantics of the Room DAOs (LIKE substring matching for
 * aliases, validUntil IS NULL filtering for "current" relations, etc.) so the tests exercise
 * real MemoryGraph behavior — not a stub.
 *
 * See `docs/architecture-decisions.md` → "Testing strategy" for the rationale.
 */
class MemoryGraphTest {

    private lateinit var entityDao: InMemoryEntityDao
    private lateinit var relationDao: InMemoryRelationDao
    private lateinit var linkDao: InMemoryLinkDao
    private lateinit var graph: MemoryGraph

    @Before
    fun setUp() {
        entityDao = InMemoryEntityDao()
        relationDao = InMemoryRelationDao()
        linkDao = InMemoryLinkDao()
        graph = MemoryGraph(entityDao, relationDao, linkDao)
    }

    // ---- resolveOrCreate: new entity ----

    @Test
    fun `resolveOrCreate creates a new entity when none exists`() = runTest {
        val ent = graph.resolveOrCreate("John Doe", EntityType.PERSON, description = "a person")

        assertEquals("john doe", ent.canonicalName)
        assertEquals("John Doe", ent.name)
        assertEquals(EntityType.PERSON, ent.type)
        assertEquals("a person", ent.description)
        assertEquals(1, entityDao.entities.size)
        assertEquals(ent.id, entityDao.findByCanonicalName("john doe")?.id)
        assertEquals(ent.id, entityDao.get(ent.id)?.id)
        assertTrue(ent.id.startsWith("ent_"))
        assertTrue(ent.createdAt > 0)
        assertEquals(ent.createdAt, ent.updatedAt)
    }

    // ---- resolveOrCreate: canonical dedup ----

    @Test
    fun `resolveOrCreate deduplicates by canonical name — John and JOHN resolve to the same entity`() = runTest {
        val first = graph.resolveOrCreate("John", EntityType.PERSON)
        val second = graph.resolveOrCreate("JOHN", EntityType.PERSON)

        assertEquals(first.id, second.id)
        assertEquals(1, entityDao.entities.size)
        // canonical name should be lowercased
        assertEquals("john", entityDao.entities.first().canonicalName)
    }

    @Test
    fun `resolveOrCreate deduplicates by canonical name — mixed case and whitespace variants`() = runTest {
        val first = graph.resolveOrCreate("John Doe", EntityType.PERSON)
        val second = graph.resolveOrCreate("JOHN   DOE", EntityType.PERSON)
        val third = graph.resolveOrCreate("john doe", EntityType.PERSON)

        assertEquals(first.id, second.id)
        assertEquals(first.id, third.id)
        assertEquals(1, entityDao.entities.size)
    }

    // ---- resolveOrCreate: alias merging ----

    @Test
    fun `resolveOrCreate merges new aliases into an existing entity`() = runTest {
        val first = graph.resolveOrCreate("John", EntityType.PERSON, aliases = listOf("Johnny"))
        // Second call carries a different alias + a description
        val second = graph.resolveOrCreate("John", EntityType.PERSON, aliases = listOf("Jack"), description = "friend")

        assertEquals(first.id, second.id)
        assertEquals(1, entityDao.entities.size)
        val stored = entityDao.entities.first()
        val aliases = stored.aliasesCsv.split(",").filter { it.isNotBlank() }
        assertTrue("merged aliases must contain 'Johnny'", aliases.contains("Johnny"))
        assertTrue("merged aliases must contain 'Jack'", aliases.contains("Jack"))
        assertEquals(2, aliases.size)
        // Description should be filled in (was blank before)
        assertEquals("friend", stored.description)
        // updatedAt should advance
        assertTrue(stored.updatedAt >= first.updatedAt)
    }

    @Test
    fun `resolveOrCreate does not overwrite an existing non-blank description`() = runTest {
        graph.resolveOrCreate("John", EntityType.PERSON, description = "first desc")
        val second = graph.resolveOrCreate("John", EntityType.PERSON, description = "second desc")
        assertEquals("first desc", second.description)
    }

    @Test
    fun `resolveOrCreate returns existing entity unchanged when no aliases or description are provided`() = runTest {
        val first = graph.resolveOrCreate("John", EntityType.PERSON)
        val second = graph.resolveOrCreate("John", EntityType.PERSON)
        assertEquals(first.id, second.id)
        assertEquals(first.aliasesCsv, second.aliasesCsv)
        assertEquals(first.description, second.description)
        assertEquals(first.updatedAt, second.updatedAt)
    }

    // ---- assertRelation: dedup ----

    @Test
    fun `assertRelation deduplicates — calling twice with same source, target, type creates only one relation`() = runTest {
        val bilal = graph.resolveOrCreate("Bilal", EntityType.PERSON)
        val acme = graph.resolveOrCreate("Acme", EntityType.PLACE)

        val first = graph.assertRelation(bilal, acme, RelationType.WORKS_AT)
        val second = graph.assertRelation(bilal, acme, RelationType.WORKS_AT)

        assertEquals(first.id, second.id)
        assertEquals(1, relationDao.relations.size)
    }

    @Test
    fun `assertRelation allows multiple relations between the same pair with different types`() = runTest {
        val bilal = graph.resolveOrCreate("Bilal", EntityType.PERSON)
        val acme = graph.resolveOrCreate("Acme", EntityType.PLACE)

        graph.assertRelation(bilal, acme, RelationType.WORKS_AT)
        graph.assertRelation(bilal, acme, RelationType.LEADS)

        assertEquals(2, relationDao.relations.size)
    }

    // ---- assertRelation: bi-temporal invalidation ----

    @Test
    fun `assertRelation allows multiple historical relations — Bilal WORKS_AT Acme (ended) then Bilal WORKS_AT Verizon (current)`() = runTest {
        val bilal = graph.resolveOrCreate("Bilal", EntityType.PERSON)
        val acme = graph.resolveOrCreate("Acme", EntityType.PLACE)
        val verizon = graph.resolveOrCreate("Verizon", EntityType.PLACE)

        // First fact: Bilal WORKS_AT Acme (currently valid)
        val acmeRel = graph.assertRelation(bilal, acme, RelationType.WORKS_AT, validFrom = 1_000L)
        assertEquals(1, relationDao.relations.size)
        assertNull(acmeRel.validUntil)

        // Bi-temporal invalidation: Acme relation ends. The MemoryGraph API itself doesn't
        // expose an invalidate method — callers go through RelationDao.invalidateEdge directly
        // (mirroring how MemoryMaintenance would). We do the same here.
        val now = 2_000L
        val invalidated = relationDao.invalidateEdge(bilal.id, acme.id, RelationType.WORKS_AT, now)
        assertEquals(1, invalidated)
        assertEquals(now, relationDao.relations.first().validUntil)

        // Second fact: Bilal WORKS_AT Verizon (currently valid).
        // assertRelation consults outgoingCurrent() which filters validUntil IS NULL, so the
        // Acme relation is no longer "current" and the Verizon assertion creates a new row.
        val verizonRel = graph.assertRelation(bilal, verizon, RelationType.WORKS_AT, validFrom = 2_500L)

        // Both relations coexist (one historical, one current) — never deleted.
        assertEquals(2, relationDao.relations.size)
        val acmeRow = relationDao.relations.first { it.targetEntityId == acme.id }
        val verizonRow = relationDao.relations.first { it.targetEntityId == verizon.id }
        assertEquals(now, acmeRow.validUntil) // ended
        assertNull(verizonRow.validUntil) // current
        assertNotEquals(acmeRow.id, verizonRow.id)
    }

    // ---- oneHopNeighbors ----

    @Test
    fun `oneHopNeighbors returns correct neighbors and excludes the seed entity`() = runTest {
        val a = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val b = graph.resolveOrCreate("Bob", EntityType.PERSON)
        val c = graph.resolveOrCreate("Carol", EntityType.PERSON)
        graph.assertRelation(a, b, RelationType.FRIEND_OF)
        graph.assertRelation(a, c, RelationType.FRIEND_OF)

        val expansion = graph.oneHopNeighbors(listOf(a.id))

        assertEquals(2, expansion.relations.size)
        val neighborIds = expansion.entities.map { it.id }.toSet()
        assertEquals(setOf(b.id, c.id), neighborIds)
        // Seed entity must NOT appear in the neighbor set
        assertTrue("seed entity should not be in its own neighbors", a.id !in neighborIds)
    }

    @Test
    fun `oneHopNeighbors returns empty expansion for empty input`() = runTest {
        val expansion = graph.oneHopNeighbors(emptyList())
        assertTrue(expansion.relations.isEmpty())
        assertTrue(expansion.entities.isEmpty())
    }

    @Test
    fun `oneHopNeighbors respects invalidation — only current relations are traversed`() = runTest {
        val a = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val b = graph.resolveOrCreate("Bob", EntityType.PERSON)
        val rel = graph.assertRelation(a, b, RelationType.FRIEND_OF)
        relationDao.invalidateEdge(a.id, b.id, RelationType.FRIEND_OF, System.currentTimeMillis())

        val expansion = graph.oneHopNeighbors(listOf(a.id))
        assertTrue("invalidated relations must not appear in oneHopNeighbors", expansion.relations.isEmpty())
        assertTrue(expansion.entities.isEmpty())
    }

    // ---- twoHopNeighbors ----

    @Test
    fun `twoHopNeighbors traverses two hops — A knows B, B knows C, expansion from A contains B and C`() = runTest {
        val a = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val b = graph.resolveOrCreate("Bob", EntityType.PERSON)
        val c = graph.resolveOrCreate("Carol", EntityType.PERSON)
        graph.assertRelation(a, b, RelationType.FRIEND_OF)
        graph.assertRelation(b, c, RelationType.FRIEND_OF)

        val expansion = graph.twoHopNeighbors(listOf(a.id))

        val entityIds = expansion.entities.map { it.id }.toSet()
        val relationCount = expansion.relations.size

        // Both neighbors should be discovered (B is 1-hop, C is 2-hop)
        assertTrue("B (1-hop) should be present", b.id in entityIds)
        assertTrue("C (2-hop) should be present", c.id in entityIds)
        // Seed (A) should not appear in the discovered neighbors
        assertTrue("seed A should not appear in its own 2-hop expansion", a.id !in entityIds)
        // Both relations should be present
        assertEquals(2, relationCount)
    }

    @Test
    fun `twoHopNeighbors returns first-hop expansion when no second hop exists`() = runTest {
        val a = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val b = graph.resolveOrCreate("Bob", EntityType.PERSON)
        graph.assertRelation(a, b, RelationType.FRIEND_OF)

        val expansion = graph.twoHopNeighbors(listOf(a.id))
        assertEquals(1, expansion.entities.size)
        assertEquals(b.id, expansion.entities.first().id)
        assertEquals(1, expansion.relations.size)
    }

    @Test
    fun `twoHopNeighbors returns empty expansion when seed has no neighbors`() = runTest {
        val a = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val expansion = graph.twoHopNeighbors(listOf(a.id))
        assertTrue(expansion.relations.isEmpty())
        assertTrue(expansion.entities.isEmpty())
    }

    // ---- canonicalize (tested indirectly through resolveOrCreate) ----

    @Test
    fun `canonicalize lowercases input`() = runTest {
        val ent = graph.resolveOrCreate("UPPERCASE NAME", EntityType.PERSON)
        assertEquals("uppercase name", ent.canonicalName)
    }

    @Test
    fun `canonicalize strips non-alphanumeric characters`() = runTest {
        val ent = graph.resolveOrCreate("O'Brien-Smith!", EntityType.PERSON)
        assertEquals("obriensmith", ent.canonicalName)
    }

    @Test
    fun `canonicalize collapses multiple whitespace into single spaces`() = runTest {
        val ent = graph.resolveOrCreate("John     Doe", EntityType.PERSON)
        assertEquals("john doe", ent.canonicalName)
    }

    @Test
    fun `canonicalize trims leading and trailing whitespace`() = runTest {
        val ent = graph.resolveOrCreate("   John Doe   ", EntityType.PERSON)
        assertEquals("john doe", ent.canonicalName)
        // The visible name should also be trimmed (MemoryGraph does name.trim())
        assertEquals("John Doe", ent.name)
    }

    @Test
    fun `canonicalize preserves digits`() = runTest {
        val ent = graph.resolveOrCreate("User 12345", EntityType.PERSON)
        assertEquals("user 12345", ent.canonicalName)
    }

    // ---- linkMemoryToEntities + memoryIdsForEntities ----

    @Test
    fun `linkMemoryToEntities writes links and memoryIdsForEntities reads them back`() = runTest {
        val alice = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val bob = graph.resolveOrCreate("Bob", EntityType.PERSON)
        graph.linkMemoryToEntities("mem_1", listOf(alice to 0.9f, bob to 0.6f))

        val memIds = graph.memoryIdsForEntities(listOf(alice.id, bob.id))
        assertEquals(listOf("mem_1"), memIds)

        // Round-trip via the link DAO directly
        assertEquals(2, linkDao.links.size)
        val aliceLink = linkDao.links.first { it.entityId == alice.id }
        assertEquals("mem_1", aliceLink.memoryId)
        assertEquals(0.9f, aliceLink.confidence, 1e-5f)
    }

    @Test
    fun `memoryIdsForEntities returns empty list for empty input`() = runTest {
        assertEquals(emptyList<String>(), graph.memoryIdsForEntities(emptyList()))
    }

    @Test
    fun `memoryIdsForEntities deduplicates memory ids across multiple entities`() = runTest {
        val alice = graph.resolveOrCreate("Alice", EntityType.PERSON)
        val bob = graph.resolveOrCreate("Bob", EntityType.PERSON)
        graph.linkMemoryToEntities("mem_shared", listOf(alice to 0.9f, bob to 0.5f))

        val memIds = graph.memoryIdsForEntities(listOf(alice.id, bob.id))
        assertEquals(listOf("mem_shared"), memIds)
    }

    // ---- searchEntities ----

    @Test
    fun `searchEntities finds entities by name substring`() = runTest {
        graph.resolveOrCreate("Alice Wonderland", EntityType.PERSON, description = "curious")
        graph.resolveOrCreate("Bob Builder", EntityType.PERSON, description = "constructive")

        val results = graph.searchEntities("Alice", limit = 10)
        assertEquals(1, results.size)
        assertEquals("Alice Wonderland", results.first().name)
    }

    // ---- in-memory DAO implementations ----
    // Hand-written to mirror the Room SQL semantics; no MockK required.

    private class InMemoryEntityDao : EntityDao {
        val entities = mutableListOf<EntityEntity>()

        override suspend fun upsert(entity: EntityEntity): Long {
            val idx = entities.indexOfFirst { it.id == entity.id }
            return if (idx >= 0) { entities[idx] = entity; idx.toLong() }
                   else { entities.add(entity); (entities.size - 1).toLong() }
        }

        override suspend fun upsertAll(entities: List<EntityEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun update(entity: EntityEntity) {
            val idx = entities.indexOfFirst { it.id == entity.id }
            if (idx >= 0) entities[idx] = entity
        }

        override suspend fun get(id: String): EntityEntity? = entities.firstOrNull { it.id == id }

        override suspend fun findByCanonicalName(canonical: String): EntityEntity? =
            entities.firstOrNull { it.canonicalName == canonical }

        // Mirrors SQL: WHERE canonicalName = :canonical OR aliasesCsv LIKE '%canonical%'
        override suspend fun matchByAlias(canonical: String): EntityEntity? =
            entities.firstOrNull { ent ->
                ent.canonicalName == canonical ||
                    ent.aliasesCsv.split(",").any { alias -> alias.trim() == canonical } ||
                    ent.aliasesCsv.contains(canonical)
            }

        override fun byTypeFlow(type: EntityType): Flow<List<EntityEntity>> =
            flowOf(entities.filter { it.type == type })

        override fun allFlow(): Flow<List<EntityEntity>> = flowOf(entities.toList())

        override suspend fun search(q: String, limit: Int): List<EntityEntity> =
            entities.filter { ent ->
                ent.name.contains(q, ignoreCase = true) ||
                    ent.aliasesCsv.contains(q, ignoreCase = true) ||
                    ent.description.contains(q, ignoreCase = true)
            }.take(limit)

        override fun countFlow(): Flow<Int> = flowOf(entities.size)

        override suspend fun delete(id: String): Int {
            val idx = entities.indexOfFirst { it.id == id }
            return if (idx >= 0) { entities.removeAt(idx); 1 } else 0
        }
    }

    private class InMemoryRelationDao : RelationDao {
        val relations = mutableListOf<RelationEntity>()

        override suspend fun upsert(relation: RelationEntity): Long {
            val idx = relations.indexOfFirst { it.id == relation.id }
            return if (idx >= 0) { relations[idx] = relation; idx.toLong() }
                   else { relations.add(relation); (relations.size - 1).toLong() }
        }

        override suspend fun upsertAll(relations: List<RelationEntity>) {
            relations.forEach { upsert(it) }
        }

        override suspend fun get(id: String): RelationEntity? = relations.firstOrNull { it.id == id }

        override suspend fun outgoingCurrent(entityId: String): List<RelationEntity> =
            relations.filter { it.sourceEntityId == entityId && it.validUntil == null }
                .sortedWith(compareByDescending<RelationEntity> { it.confidence }.thenByDescending { it.validFrom })

        override suspend fun incomingCurrent(entityId: String): List<RelationEntity> =
            relations.filter { it.targetEntityId == entityId && it.validUntil == null }
                .sortedWith(compareByDescending<RelationEntity> { it.confidence }.thenByDescending { it.validFrom })

        override suspend fun neighborsCurrent(entityIds: List<String>): List<RelationEntity> =
            relations.filter { r ->
                (r.sourceEntityId in entityIds || r.targetEntityId in entityIds) && r.validUntil == null
            }.sortedByDescending { it.confidence }

        override suspend fun invalidateEdge(sourceId: String, targetId: String, type: RelationType, now: Long): Int {
            var count = 0
            for (i in relations.indices) {
                val r = relations[i]
                if (r.sourceEntityId == sourceId && r.targetEntityId == targetId && r.type == type && r.validUntil == null) {
                    relations[i] = r.copy(validUntil = now)
                    count++
                }
            }
            return count
        }

        override fun recentFlow(limit: Int): Flow<List<RelationEntity>> =
            flowOf(relations.filter { it.validUntil == null }.sortedByDescending { it.createdAt }.take(limit))

        override fun activeCountFlow(): Flow<Int> = flowOf(relations.count { it.validUntil == null })
    }

    private class InMemoryLinkDao : MemoryEntityLinkDao {
        val links = mutableListOf<MemoryEntityLink>()

        override suspend fun upsert(link: MemoryEntityLink) {
            val idx = links.indexOfFirst { it.memoryId == link.memoryId && it.entityId == link.entityId }
            if (idx >= 0) links[idx] = link else links.add(link)
        }

        override suspend fun upsertAll(links: List<MemoryEntityLink>) {
            links.forEach { upsert(it) }
        }

        override suspend fun entitiesForMemory(memoryId: String): List<MemoryEntityLink> =
            links.filter { it.memoryId == memoryId }

        override suspend fun memoriesForEntity(entityId: String): List<MemoryEntityLink> =
            links.filter { it.entityId == entityId }

        override suspend fun memoryIdsForEntities(entityIds: List<String>): List<String> =
            links.filter { it.entityId in entityIds }.map { it.memoryId }.distinct()
    }
}
