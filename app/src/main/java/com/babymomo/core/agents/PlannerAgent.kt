package com.babymomo.core.agents

import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerAgent @Inject constructor(private val llm: LlmProvider) : Agent {
    override val id = "planner"
    override val displayName = "Planner"
    override val description = "Creates plans, roadmaps, and breaks down complex tasks"
    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun run(task: AgentTask): AgentResult {
        val t0 = System.currentTimeMillis()
        val systemPrompt = """
            You are MOMO's Planner agent. Break down a high-level goal or task into a concrete, actionable plan.

            Output format:
            GOAL: <one-sentence restatement>
            PLAN:
            1. [subtask] — [effort estimate] — [depends on]
            2. ...
            RISKS:
            - [risk 1]
            NEXT_ACTION: <the single most important next step>

            Be concrete. Prefer 3-7 subtasks.
        """.trimIndent()

        val response = llm.complete(
            listOf(LlmMessage.system(systemPrompt), LlmMessage.user(task.input)),
            LlmGenerationConfig(temperature = 0.3f, maxTokens = 800)
        ).getOrNull() ?: return AgentResult(agentId = id, status = AgentResult.AgentStatus.FAILED, output = "", error = "LLM unavailable", latencyMs = System.currentTimeMillis() - t0)

        return AgentResult(agentId = id, status = AgentResult.AgentStatus.SUCCESS, output = response.content,
            reasoning = "Planner decomposition of: ${task.input.take(80)}",
            artifacts = listOf(AgentArtifact(type = "plan", content = response.content)),
            latencyMs = System.currentTimeMillis() - t0)
    }
}
