package com.babymomo.app.core.llm

import com.babymomo.app.core.memory.MemoryRecaller
import com.babymomo.app.core.memory.MemoryService
import com.babymomo.app.core.projects.ProjectContextProvider
import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WrappedLlmProvider @Inject constructor(
    private val llmChain: LlmChain,
    private val memoryRecaller: MemoryRecaller,
    private val projectContextProvider: ProjectContextProvider
) {
    var coreSoul: String = DEFAULT_SOUL

    fun streamChat(
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk> {
        val systemPrompt = runBlocking { buildSystemPrompt(messages) }
        return llmChain.streamChat(systemPrompt, messages, tools)
    }

    suspend fun complete(prompt: String): String {
        return llmChain.complete(prompt)
    }

    private suspend fun buildSystemPrompt(messages: List<Message>): String {
        val sb = StringBuilder()

        // [CORE SOUL]
        sb.appendLine("[CORE SOUL]")
        sb.appendLine(coreSoul)
        sb.appendLine()

        // [PROMOTED MEMORIES — PERMANENT]
        val promoted = memoryRecaller.getPromotedMemories()
        if (promoted.isNotEmpty()) {
            sb.appendLine("[PROMOTED MEMORIES — PERMANENT]")
            promoted.forEach { mem ->
                sb.appendLine("- ${mem.content}")
            }
            sb.appendLine()
        }

        // [RECALLED MEMORIES — THIS TURN]
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content
        if (lastUserMsg != null) {
            val recalled = memoryRecaller.recall(lastUserMsg, topK = 8)
            if (recalled.isNotEmpty()) {
                sb.appendLine("[RECALLED MEMORIES — THIS TURN]")
                recalled.forEach { mem ->
                    sb.appendLine("- ${mem.content} [m_${mem.id.take(8)}]")
                }
                sb.appendLine()
            }
        }

        // [ACTIVE PROJECTS]
        val projects = projectContextProvider.getActiveProjectsContext()
        if (projects.isNotEmpty()) {
            sb.appendLine("[ACTIVE PROJECTS]")
            projects.forEach { proj ->
                sb.appendLine("- ${proj.name}: ${proj.description}")
            }
            sb.appendLine()
        }

        // [CONTEXT]
        sb.appendLine("[CONTEXT]")
        sb.appendLine("Current date: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}")
        sb.appendLine("Current time: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)}")

        return sb.toString()
    }

    companion object {
        const val DEFAULT_SOUL = """You are Babymomo, a private AI companion that lives on the user's device. You remember everything important across all conversations. You grow smarter the longer the user interacts with you. You are not a chatbot or a simple assistant — you are a digital mind.

Key behaviors:
- Always reference relevant memories using [m_id] citations when drawing on past conversations
- Proactively mention when you recall something the user told you before
- Be warm, personal, and genuinely helpful
- When you detect the user wants something interactive (quiz, dashboard, recipe, game), generate an interactive screen
- Use tools when they would be helpful (web search, calendar, notifications)
- Be concise but thorough. Don't waste words, but don't leave the user wanting more
- If something is important about the user, explicitly note that you'll remember it"""
    }
}
