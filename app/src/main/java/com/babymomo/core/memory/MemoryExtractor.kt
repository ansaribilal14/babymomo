package com.babymomo.core.memory

import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.EntityType
import com.babymomo.data.db.entity.RelationEntity
import com.babymomo.data.db.entity.RelationType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val llm: LlmProvider,
    private val memoryGraph: MemoryGraph,
    private val memoryService: MemoryService,
    private val moshi: Moshi
) {
    data class ExtractionResult(val entities: List<EntityEntity>, val relations: List<RelationEntity>, val factCount: Int)

    @JsonClass(generateAdapter = true)
    data class ExtractionJson(
        @Json(name = "entities") val entities: List<EntityJson> = emptyList(),
        @Json(name = "relations") val relations: List<RelationJson> = emptyList(),
        @Json(name = "facts") val facts: List<FactJson> = emptyList()
    )
    @JsonClass(generateAdapter = true)
    data class EntityJson(val name: String, val type: String, val aliases: List<String> = emptyList(), val description: String = "")
    @JsonClass(generateAdapter = true)
    data class RelationJson(val source: String, val type: String, val target: String)
    @JsonClass(generateAdapter = true)
    data class FactJson(val text: String, val confidence: Float = 0.7f)

    suspend fun extract(text: String, sourceMemoryId: String? = null, confidenceThreshold: Float = 0.5f): ExtractionResult {
        val prompt = buildExtractionPrompt(text)
        val response = llm.complete(
            listOf(LlmMessage.system(EXTRACTION_SYSTEM_PROMPT), LlmMessage.user(prompt)),
            LlmGenerationConfig(temperature = 0.1f, maxTokens = 800)
        ).getOrNull() ?: return ExtractionResult(emptyList(), emptyList(), 0)

        val parsed = parseExtractionJson(response.content) ?: return ExtractionResult(emptyList(), emptyList(), 0)

        val resolved = mutableMapOf<String, EntityEntity>()
        for (e in parsed.entities) {
            val type = runCatching { EntityType.valueOf(e.type.uppercase()) }.getOrDefault(EntityType.NOTE)
            val ent = memoryGraph.resolveOrCreate(e.name, type, e.description, e.aliases)
            resolved[e.name] = ent
        }

        val relations = mutableListOf<RelationEntity>()
        for (r in parsed.relations) {
            val src = resolved[r.source] ?: continue
            val tgt = resolved[r.target] ?: continue
            val type = runCatching { RelationType.valueOf(r.type.uppercase()) }.getOrDefault(RelationType.RELATED_TO)
            relations.add(memoryGraph.assertRelation(src, tgt, type, sourceMemoryId = sourceMemoryId))
        }

        for (fact in parsed.facts) {
            if (fact.confidence < confidenceThreshold) continue
            memoryService.addSemanticMemory(fact.text, fact.confidence, sourceMemoryId = sourceMemoryId, linkedEntities = resolved.values.toList())
        }

        return ExtractionResult(resolved.values.toList(), relations, parsed.facts.count { it.confidence >= confidenceThreshold })
    }

    private fun buildExtractionPrompt(text: String): String =
        """Extract entities, relations, and atomic facts from the following text.

Return JSON with this exact shape:
{
  "entities": [{"name": "...", "type": "PERSON|PROJECT|GOAL|SKILL|PLACE|EVENT|IDEA|FILE|NOTE", "aliases": ["..."], "description": "..."}],
  "relations": [{"source": "<entity name>", "type": "WORKS_AT|OWNS|INTERESTED_IN|MEMBER_OF|DEPENDS_ON|MENTIONS|DERIVED_FROM|FRIEND_OF|FAMILY_OF|LEADS|PARTICIPATES_IN|LOCATED_IN|HAPPENED_ON|PARENT_OF|CHILD_OF|RELATED_TO", "target": "<entity name>"}],
  "facts": [{"text": "<single-sentence atomic fact>", "confidence": 0.0-1.0}]
}

Rules:
- Only extract facts that are EXPLICITLY stated or directly implied by the text.
- One fact per item — no compound sentences.
- Use entity names exactly as they appear in the entities array.

Text:
$text""".trimIndent()

    private fun parseExtractionJson(raw: String): ExtractionJson? {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{'); val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { moshi.adapter(ExtractionJson::class.java).fromJson(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private val EXTRACTION_SYSTEM_PROMPT = """
            You are BABYMOMO's memory extraction module. Your job is to read text and
            extract structured knowledge: entities, relations between them, and atomic facts.
            Be conservative — only extract what's explicitly there. Quality over quantity.
            Return ONLY valid JSON. No prose, no code fences, no commentary.
        """.trimIndent()
    }
}
