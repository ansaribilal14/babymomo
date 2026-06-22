package com.babymomo.core.agents

import com.babymomo.core.memory.MemoryService
import com.babymomo.data.db.entity.MemorySource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryAgent @Inject constructor(private val memoryService: MemoryService) : Agent {
    override val id = "memory"
    override val displayName = "Memory"
    override val description = "Stores, retrieves, and connects memories"
    override suspend fun isAvailable(): Boolean = true

    override suspend fun run(task: AgentTask): AgentResult {
        val t0 = System.currentTimeMillis()
        val input = task.input.trim()
        val isWrite = input.lowercase().startsWith("remember") ||
                      input.lowercase().startsWith("note:") ||
                      input.lowercase().startsWith("don't forget")

        return if (isWrite) {
            val mem = memoryService.addSemanticMemory(input, confidence = 1.0f, source = MemorySource.USER_STATED)
            AgentResult(agentId = id, status = AgentResult.AgentStatus.SUCCESS,
                output = "Stored as semantic memory: ${mem.id}",
                reasoning = "Detected intentional memory write",
                artifacts = listOf(AgentArtifact(type = "memory_write", content = mem.content, metadata = mapOf("memoryId" to mem.id))),
                latencyMs = System.currentTimeMillis() - t0)
        } else {
            val results = memoryService.searchContent(input, limit = 10)
            val summary = if (results.isEmpty()) "No matching memories found."
            else results.joinToString("\n") { m -> "- [${m.id}] ${m.content}" }
            AgentResult(agentId = id, status = AgentResult.AgentStatus.SUCCESS, output = summary,
                reasoning = "Searched ${results.size} matching memories",
                artifacts = listOf(AgentArtifact(type = "memory_recall", content = summary)),
                latencyMs = System.currentTimeMillis() - t0)
        }
    }
}
