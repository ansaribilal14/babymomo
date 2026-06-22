package com.babymomo.core.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockLlmProvider @Inject constructor() : LlmProvider {
    override val name: String = "mock"
    override suspend fun isAvailable(): Boolean = true
    override suspend fun status(): String = "Mock brain (no model loaded) — ready for demo"

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> {
        val t0 = System.currentTimeMillis()
        val userMsg = messages.lastOrNull { it.role == LlmRole.USER }?.content.orEmpty()
        val reply = generateReply(userMsg)
        delay(120)
        return Result.success(LlmResponse(
            content = reply,
            tokensIn = (userMsg.length / 4).coerceAtLeast(1),
            tokensOut = (reply.length / 4).coerceAtLeast(1),
            latencyMs = System.currentTimeMillis() - t0,
            providerName = name, modelName = "mock-v0.1"
        ))
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> = flow {
        val userMsg = messages.lastOrNull { it.role == LlmRole.USER }?.content.orEmpty()
        val reply = generateReply(userMsg)
        val tokens = reply.split(Regex("(?=\\s)|(?<=\\s)|(?<=[,.!?])|(?=[,.!?])")).filter { it.isNotEmpty() }
        for (tok in tokens) {
            emit(tok)
            delay(20L + (30 * (tok.length.coerceAtMost(6) / 6.0)).toLong())
        }
    }

    private fun generateReply(userInput: String): String {
        val input = userInput.trim().lowercase()
        return when {
            input.isEmpty() -> "I'm listening — tell me more."
            input.startsWith("hi") || input.startsWith("hello") || input.startsWith("hey") ->
                "Hello. I'm MOMO. I'm still learning about you — what's on your mind today?"
            "remember" in input ->
                "Got it. I'll hold onto: \"$userInput\" — once you download a model, I'll store it as a real memory with entities and relations."
            "project" in input ->
                "Tell me more about this project — its goal, the people involved, and any deadlines. Once I have a real model, I'll create a living project entity you can revisit any time."
            "what do you know about me" in input || "what do you remember" in input ->
                "Right now (running on the mock brain) — not much yet. Download a model from the Models tab and we'll start building your memory graph together."
            input.endsWith("?") ->
                "That's a great question. With a real model loaded, I'd search your memory graph first, then reason over what I find. Try downloading Gemma 2B from the Models tab to see this in action."
            else ->
                "I hear you: \"$userInput\". (I'm currently running on the mock brain — download a model from the Models tab to unlock real reasoning, memory, and agent orchestration.)"
        }
    }
}
