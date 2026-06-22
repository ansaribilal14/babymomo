package com.babymomo.core.memory

import com.babymomo.data.db.dao.EntityDao
import com.babymomo.data.db.dao.MemoryEntityLinkDao
import com.babymomo.data.db.dao.RelationDao
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.EntityType
import com.babymomo.data.db.entity.MemoryEntityLink
import com.babymomo.data.db.entity.RelationEntity
import com.babymomo.data.db.entity.RelationType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** MemoryGraph — the knowledge-graph layer. Bi-temporal model adapted from Zep/Graphiti. */
@Singleton
class MemoryGraph @Inject constructor(
    private val entityDao: EntityDao,
    private val relationDao: RelationDao,
    private val linkDao: MemoryEntityLinkDao
) {
    suspend fun resolveOrCreate(name: String, type: EntityType, description: String = "", aliases: List<String> = emptyList()): EntityEntity {
        val canonical = canonicalize(name)
        val existing = entityDao.findByCanonicalName(canonical) ?: entityDao.matchByAlias(canonical)
        if (existing != null) {
            if (aliases.isNotEmpty() || description.isNotBlank()) {
                val mergedAliases = (aliases + existing.aliasesCsv.split(",").filter { it.isNotBlank() }).distinct()
                val mergedDesc = if (existing.description.isBlank()) description else existing.description
                val updated = existing.copy(aliasesCsv = mergedAliases.joinToString(","), description = mergedDesc, updatedAt = System.currentTimeMillis())
                entityDao.update(updated)
                return updated
            }
            return existing
        }
        val now = System.currentTimeMillis()
        val entity = EntityEntity(
            id = "ent_" + UUID.randomUUID().toString().take(16),
            type = type, name = name.trim(), canonicalName = canonical,
            aliasesCsv = aliases.joinToString(","), description = description,
            createdAt = now, updatedAt = now
        )
        entityDao.upsert(entity)
        return entity
    }

    suspend fun searchEntities(query: String, limit: Int = 30): List<EntityEntity> = entityDao.search(query, limit)

    suspend fun assertRelation(source: EntityEntity, target: EntityEntity, type: RelationType, confidence: Float = 0.8f, validFrom: Long = System.currentTimeMillis(), sourceMemoryId: String? = null): RelationEntity {
        val outgoing = relationDao.outgoingCurrent(source.id)
        val existing = outgoing.firstOrNull { it.targetEntityId == target.id && it.type == type }
        if (existing != null) return existing
        val rel = RelationEntity(
            id = "rel_" + UUID.randomUUID().toString().take(16),
            sourceEntityId = source.id, targetEntityId = target.id, type = type,
            confidence = confidence, validFrom = validFrom, validUntil = null,
            sourceMemoryId = sourceMemoryId, createdAt = System.currentTimeMillis()
        )
        relationDao.upsert(rel)
        return rel
    }

    suspend fun linkMemoryToEntities(memoryId: String, entities: List<Pair<EntityEntity, Float>>) {
        val now = System.currentTimeMillis()
        linkDao.upsertAll(entities.map { (e, conf) ->
            MemoryEntityLink(memoryId = memoryId, entityId = e.id, confidence = conf, extractedAt = now)
        })
    }

    suspend fun oneHopNeighbors(entityIds: List<String>): GraphExpansion {
        if (entityIds.isEmpty()) return GraphExpansion(emptyList(), emptyList())
        val rels = relationDao.neighborsCurrent(entityIds)
        val neighborIds = rels.flatMap { listOf(it.sourceEntityId, it.targetEntityId) }.distinct() - entityIds.toSet()
        val neighbors = neighborIds.mapNotNull { id -> entityDao.get(id) }
        return GraphExpansion(relations = rels, entities = neighbors)
    }

    suspend fun twoHopNeighbors(entityIds: List<String>): GraphExpansion {
        val first = oneHopNeighbors(entityIds)
        if (first.entities.isEmpty()) return first
        val second = oneHopNeighbors(first.entities.map { it.id })
        val allRels = (first.relations + second.relations).distinctBy { it.id }
        val allEntities = (first.entities + second.entities).distinctBy { it.id }
        return GraphExpansion(relations = allRels, entities = allEntities)
    }

    suspend fun memoryIdsForEntities(entityIds: List<String>): List<String> {
        if (entityIds.isEmpty()) return emptyList()
        return linkDao.memoryIdsForEntities(entityIds)
    }

    private fun canonicalize(name: String): String =
        name.lowercase().trim().replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ").take(128)

    data class GraphExpansion(val relations: List<RelationEntity>, val entities: List<EntityEntity>)
}
