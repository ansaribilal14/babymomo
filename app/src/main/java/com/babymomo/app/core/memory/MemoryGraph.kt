package com.babymomo.app.core.memory

import com.babymomo.app.data.db.dao.EntityDao
import com.babymomo.app.data.db.dao.RelationDao
import com.babymomo.app.data.db.entities.EntityEntity
import com.babymomo.app.data.db.entities.RelationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryGraph @Inject constructor(
    private val entityDao: EntityDao,
    private val relationDao: RelationDao
) {
    suspend fun addEntity(name: String, type: String, description: String? = null, projectId: String? = null): EntityEntity {
        val entity = EntityEntity(
            id = "ent_${System.currentTimeMillis()}_${name.hashCode()}",
            name = name,
            type = type,
            description = description,
            createdAt = System.currentTimeMillis(),
            projectId = projectId
        )
        entityDao.insert(entity)
        return entity
    }

    suspend fun addRelation(fromId: String, toId: String, type: String, weight: Double = 1.0): RelationEntity {
        val relation = RelationEntity(
            id = "rel_${System.currentTimeMillis()}_${type.hashCode()}",
            fromEntityId = fromId,
            toEntityId = toId,
            type = type,
            weight = weight,
            validFrom = System.currentTimeMillis()
        )
        relationDao.insert(relation)
        return relation
    }

    suspend fun getGraphProximity(entityIds: List<String>, targetEntityIds: List<String>): Double {
        if (entityIds.isEmpty() || targetEntityIds.isEmpty()) return 0.0

        val allRelations = mutableListOf<RelationEntity>()
        entityIds.forEach { eid ->
            allRelations.addAll(relationDao.getByEntity(eid))
        }

        var proximity = 0.0
        var count = 0
        for (rel in allRelations) {
            if (rel.fromEntityId in targetEntityIds || rel.toEntityId in targetEntityIds) {
                proximity += 1.0  // Direct connection
                count++
            }
        }

        return if (count > 0) proximity / count else 0.0
    }

    suspend fun findOrCreateEntity(name: String, type: String): EntityEntity {
        val existing = entityDao.search(name)
        return existing.firstOrNull { it.type == type }
            ?: addEntity(name, type)
    }
}
