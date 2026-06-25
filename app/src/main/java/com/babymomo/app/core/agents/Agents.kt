package com.babymomo.app.core.agents

import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.core.llm.model.Message
import javax.inject.Inject
import javax.inject.Singleton

interface Agent {
    val name: String
    val description: String
    suspend fun process(messages: List<Message>): String
}

@Singleton
class PlannerAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "PlannerAgent"
    override val description = "Breaks complex tasks into actionable steps"

    override suspend fun process(messages: List<Message>): String {
        val prompt = "Create a step-by-step plan for: ${messages.lastOrNull()?.content ?: ""}"
        return llmProvider.complete(prompt)
    }
}

@Singleton
class ResearcherAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "ResearcherAgent"
    override val description = "Gathers information and provides research summaries"

    override suspend fun process(messages: List<Message>): String {
        val prompt = "Research and provide a comprehensive summary about: ${messages.lastOrNull()?.content ?: ""}"
        return llmProvider.complete(prompt)
    }
}

@Singleton
class MemoryAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "MemoryAgent"
    override val description = "Recalls and organizes memories about the user"

    override suspend fun process(messages: List<Message>): String {
        val prompt = "Based on what you know about the user, summarize relevant memories about: ${messages.lastOrNull()?.content ?: ""}"
        return llmProvider.complete(prompt)
    }
}

@Singleton
class CriticAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "CriticAgent"
    override val description = "Reviews and improves responses for quality"

    override suspend fun process(messages: List<Message>): String {
        val prompt = "Review and improve this response, making it more accurate and helpful: ${messages.lastOrNull()?.content ?: ""}"
        return llmProvider.complete(prompt)
    }
}

@Singleton
class ExecutorAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val skillRegistry: com.babymomo.app.core.skills.SkillRegistry
) : Agent {
    override val name = "ExecutorAgent"
    override val description = "Executes skills and tools based on user requests"

    override suspend fun process(messages: List<Message>): String {
        val content = messages.lastOrNull()?.content ?: ""
        val matchedSkill = skillRegistry.matchSkill(content)
        return if (matchedSkill != null) {
            matchedSkill.execute(content)
        } else {
            llmProvider.complete(content)
        }
    }
}

@Singleton
class AgentOrchestrator @Inject constructor(
    private val plannerAgent: PlannerAgent,
    private val researcherAgent: ResearcherAgent,
    private val memoryAgent: MemoryAgent,
    private val criticAgent: CriticAgent,
    private val executorAgent: ExecutorAgent
) {
    suspend fun orchestrate(messages: List<Message>, complexity: TaskComplexity): String {
        return when (complexity) {
            TaskComplexity.Simple -> executorAgent.process(messages)
            TaskComplexity.Moderate -> {
                val plan = plannerAgent.process(messages)
                val execution = executorAgent.process(messages)
                "$plan\n\n$execution"
            }
            TaskComplexity.Complex -> {
                val research = researcherAgent.process(messages)
                val plan = plannerAgent.process(messages)
                val execution = executorAgent.process(messages)
                val review = criticAgent.process(listOf(Message.assistant("$research\n$plan\n$execution")))
                review
            }
        }
    }

    enum class TaskComplexity { Simple, Moderate, Complex }
}
