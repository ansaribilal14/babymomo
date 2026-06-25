package com.babymomo.app.core.llm

import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockLlmProvider @Inject constructor() : LlmProvider {

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: "Hello"
        val response = generateMockResponse(lastUserMsg)

        // Simulate word-by-word streaming
        response.split(" ").forEachIndexed { index, word ->
            emit(LlmChunk.Token(if (index == 0) word else " $word"))
            kotlinx.coroutines.delay(30)
        }
        emit(LlmChunk.Done)
    }

    override suspend fun complete(prompt: String): String {
        return generateMockResponse(prompt)
    }

    override fun isAvailable(): Boolean = true

    override fun providerName(): String = "Mock"

    private fun generateMockResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") ->
                "Hello! I'm Babymomo, your personal AI companion. I remember everything we talk about and I'm here to help. What would you like to do today?"
            lower.contains("remember") ->
                "I've noted that down. My memory system will store this as an important fact about you. The more we interact, the better I'll know you. [m_${System.currentTimeMillis() % 1000}]"
            lower.contains("search") || lower.contains("web") ->
                "I'd search the web for that right now, but my web search tool needs to be configured first. You can enable it in Settings > Tools. For now, I'm working from my built-in knowledge."
            lower.contains("calendar") || lower.contains("schedule") ->
                "I can help with calendar management! My calendar tool can read and create events. Just make sure you've granted calendar permissions in Settings."
            lower.contains("memory") || lower.contains("memories") ->
                "My memory system tracks four types of memories: Working (short-term), Episodic (events), Semantic (facts), and Procedural (preferences). You can browse them in the Memory tab."
            lower.contains("project") ->
                "I can help you manage projects! Head to the Projects tab to create one, and I'll automatically link it to my knowledge graph so I always have context about what you're working on."
            lower.contains("help") ->
                "Here's what I can do:\n• Chat with full memory across conversations\n• Search the web for current info\n• Manage your calendar\n• Run shell commands in a Linux sandbox\n• Generate interactive screens (quizzes, dashboards, recipes)\n• Check on you via heartbeat every 30 minutes\n\nWhat interests you?"
            else ->
                "I hear you. As Babymomo, I'm designed to remember our conversations and grow smarter over time. Every interaction helps me understand you better. Feel free to tell me anything — I'll remember what matters. [m_${System.currentTimeMillis() % 1000}]"
        }
    }
}
