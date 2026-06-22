package com.babymomo.core.agents

import com.babymomo.core.kernel.RoutingDecision
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    private val planner: PlannerAgent,
    private val researcher: ResearchAgent,
    private val critic: CriticAgent,
    private val executor: ExecutorAgent
) {
    suspend fun run(userMessage: String, routing: RoutingDecision): String {
        val sb = StringBuilder()

        if (routing.needResearch) {
            val result = researcher.run(AgentTask(description = "Research context for user query", input = userMessage, maxSteps = 1))
            if (result.status != AgentResult.AgentStatus.FAILED) {
                sb.append("[Research]\n").append(result.output).append("\n\n")
            }
        }

        if (routing.needPlanning) {
            val planResult = planner.run(AgentTask(description = "Plan steps for user query", input = userMessage, maxSteps = 1))
            if (planResult.status != AgentResult.AgentStatus.FAILED) {
                sb.append("[Plan]\n").append(planResult.output).append("\n\n")
            }
            if (routing.needCritic && planResult.status == AgentResult.AgentStatus.SUCCESS) {
                val criticResult = critic.run(AgentTask(description = "Verify the plan", input = "Plan to verify:\n${planResult.output}", maxSteps = 1))
                if (criticResult.status != AgentResult.AgentStatus.FAILED) {
                    sb.append("[Critic]\n").append(criticResult.output).append("\n\n")
                }
            }
        }

        if (routing.needTools) {
            val execResult = executor.run(AgentTask(description = "Execute matching skill", input = userMessage, maxSteps = 1))
            if (execResult.status != AgentResult.AgentStatus.FAILED && execResult.status != AgentResult.AgentStatus.SKIPPED) {
                sb.append("[Action]\n").append(execResult.output).append("\n\n")
            }
        }

        return sb.toString().trim().ifEmpty { "" }
    }
}
