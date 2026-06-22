package com.babymomo.core.agents

import com.babymomo.core.skills.SkillRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutorAgent @Inject constructor(private val skillRegistry: SkillRegistry) : Agent {
    override val id = "executor"
    override val displayName = "Executor"
    override val description = "Performs actions and runs skills"
    override suspend fun isAvailable(): Boolean = true

    override suspend fun run(task: AgentTask): AgentResult {
        val t0 = System.currentTimeMillis()
        val skill = skillRegistry.findSkillForInput(task.input)
            ?: return AgentResult(agentId = id, status = AgentResult.AgentStatus.SKIPPED,
                output = "No matching skill found for: ${task.input.take(60)}",
                reasoning = "Skill registry returned no match",
                latencyMs = System.currentTimeMillis() - t0)

        return runCatching {
            val result = skill.execute(task.input)
            AgentResult(agentId = id,
                status = if (result.success) AgentResult.AgentStatus.SUCCESS else AgentResult.AgentStatus.PARTIAL,
                output = result.output, reasoning = "Executed skill: ${skill.id}",
                artifacts = listOf(AgentArtifact(type = "skill_result", content = result.output, metadata = mapOf("skillId" to skill.id))),
                latencyMs = System.currentTimeMillis() - t0)
        }.getOrElse {
            AgentResult(agentId = id, status = AgentResult.AgentStatus.FAILED, output = "",
                error = it.message ?: "Skill execution failed",
                latencyMs = System.currentTimeMillis() - t0)
        }
    }
}
