package com.babymomo.core.agents

import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CriticAgent @Inject constructor(private val llm: LlmProvider) : Agent {
    override val id = "critic"
    override val displayName = "Critic"
    override val description = "Checks for mistakes and verifies logic"
    override suspend fun isAvailable(): Boolean = llm.isAvailable()

    override suspend fun run(task: AgentTask): AgentResult {
        val t0 = System.currentTimeMillis()
        val systemPrompt = """
            You are MOMO's Critic agent. Find flaws in a proposed answer or plan.

            Output format:
            VERDICT: PASS | REVISE | REJECT
            ISSUES:
            - [issue 1, severity: high/med/low]
            SUGGESTED_FIX: <one-sentence fix if REVISE or REJECT>

            If the answer is solid, return PASS with no issues. Don't manufacture problems.
        """.trimIndent()

        val response = llm.complete(
            listOf(LlmMessage.system(systemPrompt), LlmMessage.user(task.input)),
            LlmGenerationConfig(temperature = 0.1f, maxTokens = 500)
        ).getOrNull() ?: return AgentResult(agentId = id, status = AgentResult.AgentStatus.FAILED, output = "", error = "LLM unavailable", latencyMs = System.currentTimeMillis() - t0)

        val verdict = if (response.content.contains("VERDICT: PASS", ignoreCase = true)) AgentResult.AgentStatus.SUCCESS
                      else if (response.content.contains("VERDICT: REVISE", ignoreCase = true)) AgentResult.AgentStatus.PARTIAL
                      else AgentResult.AgentStatus.SUCCESS

        return AgentResult(agentId = id, status = verdict, output = response.content, reasoning = "Critic verification",
            artifacts = listOf(AgentArtifact(type = "critique", content = response.content)),
            latencyMs = System.currentTimeMillis() - t0)
    }
}
