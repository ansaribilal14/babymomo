package com.babymomo.core.agents

import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import com.babymomo.core.memory.MemoryRecaller
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResearchAgent @Inject constructor(
    private val llm: LlmProvider,
    private val recaller: MemoryRecaller
) : Agent {
    override val id = "researcher"
    override val displayName = "Researcher"
    override val description = "Collects information, analyzes, and summarizes"
    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun run(task: AgentTask): AgentResult {
        val t0 = System.currentTimeMillis()
        val recall = recaller.recall(task.input, topK = 12)
        val memoryContext = if (recall.memories.isEmpty()) "No relevant memories found."
        else recall.memories.joinToString("\n") { m -> "- [${m.id}] (conf=${m.confidence}) ${m.content}" }

        val systemPrompt = """
            You are MOMO's Research agent. Analyze available information and produce a concise, structured summary.

            You have access to the following memories from the user's knowledge graph:
            $memoryContext

            Output format:
            FINDINGS:
            - [key finding 1, with memory reference like [m_abc]]
            ANALYSIS: <2-3 sentence interpretation>
            GAPS: <what's missing or uncertain>
            RECOMMENDATION: <next step if any>
        """.trimIndent()

        val response = llm.complete(
            listOf(LlmMessage.system(systemPrompt), LlmMessage.user(task.input)),
            LlmGenerationConfig(temperature = 0.2f, maxTokens = 800)
        ).getOrNull() ?: return AgentResult(agentId = id, status = AgentResult.AgentStatus.FAILED, output = "", error = "LLM unavailable", latencyMs = System.currentTimeMillis() - t0)

        return AgentResult(agentId = id, status = AgentResult.AgentStatus.SUCCESS, output = response.content,
            reasoning = "Researched via ${recall.memories.size} memories",
            artifacts = listOf(AgentArtifact(type = "research_summary", content = response.content)),
            latencyMs = System.currentTimeMillis() - t0)
    }
}
