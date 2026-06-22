package com.babymomo.core.agents

interface Agent {
    val id: String
    val displayName: String
    val description: String
    suspend fun isAvailable(): Boolean
    suspend fun run(task: AgentTask): AgentResult
}

data class AgentTask(val description: String, val input: String, val context: String = "", val maxSteps: Int = 3, val tags: List<String> = emptyList())

data class AgentResult(
    val agentId: String, val status: AgentStatus, val output: String,
    val reasoning: String = "", val artifacts: List<AgentArtifact> = emptyList(),
    val error: String? = null, val latencyMs: Long = 0
) {
    enum class AgentStatus { SUCCESS, PARTIAL, FAILED, SKIPPED }
}

data class AgentArtifact(val type: String, val content: String, val metadata: Map<String, String> = emptyMap())
