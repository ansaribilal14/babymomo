package com.babymomo.app.core.kernel

import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.core.llm.model.LlmChunk
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.core.llm.model.Tool
import com.babymomo.app.core.memory.MemoryService
import com.babymomo.app.core.tools.ToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestClassifier @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) {
    suspend fun classify(userMessage: String): RouteType {
        val lower = userMessage.lowercase()
        return when {
            lower.contains("quiz") || lower.contains("dashboard") ||
            lower.contains("recipe") || lower.contains("game") ||
            lower.contains("interactive") || lower.contains("show me a") ->
                RouteType.Interactive

            lower.contains("write") || lower.contains("summarize") ||
            lower.contains("search") || lower.contains("calendar") ||
            lower.contains("shell") || lower.contains("run") ->
                RouteType.Skill

            lower.contains("plan") || lower.contains("research") ||
            lower.contains("analyze") || lower.contains("critique") ->
                RouteType.Agent

            else -> RouteType.Chat
        }
    }

    enum class RouteType {
        Chat, Skill, Agent, Interactive
    }
}

@Singleton
class MomoKernel @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val requestClassifier: RequestClassifier,
    private val memoryService: MemoryService,
    private val toolRegistry: ToolRegistry
) {
    fun streamProcess(messages: List<Message>): Flow<KernelOutput> = flow {
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val route = requestClassifier.classify(lastUserMsg)

        val tools = toolRegistry.getAvailableTools()
        val toolDefs = tools.map { Tool(it.name, it.description, it.parameters) }

        val chunkFlow = llmProvider.streamChat(messages, toolDefs)

        val fullResponse = StringBuilder()
        var routingReason = route.name

        chunkFlow.collect { chunk ->
            when (chunk) {
                is LlmChunk.Token -> {
                    fullResponse.append(chunk.text)
                    emit(KernelOutput.Token(chunk.text))
                }
                is LlmChunk.ToolCall -> {
                    val result = toolRegistry.execute(chunk.name, chunk.input.toString())
                    emit(KernelOutput.ToolUsed(chunk.name, result))
                }
                is LlmChunk.Done -> {
                    // Process memory after response
                    try {
                        memoryService.processConversationTurn(lastUserMsg, fullResponse.toString())
                    } catch (_: Exception) { }
                    emit(KernelOutput.Done(routingReason))
                }
                is LlmChunk.Error -> {
                    emit(KernelOutput.Error(chunk.message))
                }
                is LlmChunk.ToolResult -> {
                    // Tool result handled internally
                }
            }
        }
    }
}

sealed class KernelOutput {
    data class Token(val text: String) : KernelOutput()
    data class ToolUsed(val toolName: String, val result: String) : KernelOutput()
    data class Done(val routingReason: String) : KernelOutput()
    data class Error(val message: String) : KernelOutput()
}
