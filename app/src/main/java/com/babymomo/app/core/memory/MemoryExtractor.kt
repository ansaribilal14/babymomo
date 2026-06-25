package com.babymomo.app.core.memory

import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.data.db.dao.MemoryDao
import com.babymomo.app.data.db.entities.MemoryEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun extract(userMessage: String, assistantResponse: String): ExtractionResult {
        val prompt = """You are a memory extraction agent. From the conversation below, extract:
1. ENTITIES: named people, places, projects, or concepts mentioned
2. RELATIONS: how entities relate to each other
3. FACTS: specific facts about the user worth remembering long-term
4. TYPE: classify each fact as WORKING / EPISODIC / SEMANTIC / PROCEDURAL

User: $userMessage
Assistant: $assistantResponse

Respond ONLY with JSON. No prose. Schema:
{
  "entities": [{"name": "", "type": "PERSON|PLACE|CONCEPT|PROJECT|THING", "description": ""}],
  "relations": [{"from": "", "to": "", "type": ""}],
  "memories": [{"content": "", "type": "WORKING|EPISODIC|SEMANTIC|PROCEDURAL", "confidence": 0.9}]
}"""

        return try {
            val response = llmProvider.complete(prompt)
            parseExtraction(response)
        } catch (_: Exception) {
            ExtractionResult(emptyList(), emptyList(), emptyList())
        }
    }

    private fun parseExtraction(response: String): ExtractionResult {
        return try {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val parsed = json.parseToJsonElement(cleaned).jsonObject

            val entities = parsed["entities"]?.jsonArray?.mapNotNull { elem ->
                val obj = elem.jsonObject
                ExtractionEntity(
                    name = obj["name"]?.toString()?.trim('"') ?: return@mapNotNull null,
                    type = obj["type"]?.toString()?.trim('"') ?: "CONCEPT",
                    description = obj["description"]?.toString()?.trim('"')
                )
            } ?: emptyList()

            val relations = parsed["relations"]?.jsonArray?.mapNotNull { elem ->
                val obj = elem.jsonObject
                ExtractionRelation(
                    from = obj["from"]?.toString()?.trim('"') ?: return@mapNotNull null,
                    to = obj["to"]?.toString()?.trim('"') ?: return@mapNotNull null,
                    type = obj["type"]?.toString()?.trim('"') ?: "RELATED_TO"
                )
            } ?: emptyList()

            val memories = parsed["memories"]?.jsonArray?.mapNotNull { elem ->
                val obj = elem.jsonObject
                ExtractionMemory(
                    content = obj["content"]?.toString()?.trim('"') ?: return@mapNotNull null,
                    type = obj["type"]?.toString()?.trim('"') ?: "EPISODIC",
                    confidence = obj["confidence"]?.toString()?.toDoubleOrNull() ?: 0.8
                )
            } ?: emptyList()

            ExtractionResult(entities, relations, memories)
        } catch (_: Exception) {
            ExtractionResult(emptyList(), emptyList(), emptyList())
        }
    }
}

data class ExtractionResult(
    val entities: List<ExtractionEntity>,
    val relations: List<ExtractionRelation>,
    val memories: List<ExtractionMemory>
)

data class ExtractionEntity(val name: String, val type: String, val description: String?)
data class ExtractionRelation(val from: String, val to: String, val type: String)
data class ExtractionMemory(val content: String, val type: String, val confidence: Double)
