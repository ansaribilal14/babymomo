package com.babymomo.core.kernel

import com.babymomo.core.agents.AgentOrchestrator
import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import com.babymomo.core.memory.MemoryExtractor
import com.babymomo.core.memory.MemoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomoKernel @Inject constructor(
    private val llmProvider: LlmProvider,
    private val classifier: RequestClassifier,
    private val orchestrator: AgentOrchestrator,
    private val memoryExtractor: MemoryExtractor,
    private val memoryService: MemoryService
) {
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun process(userMessage: String, conversationHistory: List<LlmMessage> = emptyList()): KernelResult {
        val routing = classifier.classify(userMessage)
        val orchestratorContext = if (routing.needPlanning || routing.needResearch || routing.needCritic) {
            runCatching { orchestrator.run(userMessage, routing) }.getOrNull()
        } else null

        val messages = buildList {
            if (orchestratorContext != null) add(LlmMessage.system("Agent context:\n$orchestratorContext"))
            addAll(conversationHistory)
            add(LlmMessage.user(userMessage))
        }

        val response = llmProvider.complete(messages, LlmGenerationConfig(maxTokens = 1024))
            .getOrElse { return KernelResult(error = it.message ?: "Unknown error", routing = routing) }

        bgScope.launch {
            runCatching {
                memoryService.addEpisodicMemory("User: $userMessage", confidence = 1.0f, tags = listOf("conversation"))
                memoryService.addEpisodicMemory("MOMO: ${response.content}", confidence = 0.9f,
                    source = com.babymomo.data.db.entity.MemorySource.LLM_INFERRED, tags = listOf("conversation", "response"))
                memoryExtractor.extract("User: $userMessage\nMOMO: ${response.content}", confidenceThreshold = 0.6f)
            }
        }
        return KernelResult(content = response.content, routing = routing, tokensIn = response.tokensIn,
            tokensOut = response.tokensOut, latencyMs = response.latencyMs, providerName = response.providerName)
    }

    fun streamProcess(userMessage: String, conversationHistory: List<LlmMessage> = emptyList()): Flow<KernelStreamEvent> = flow {
        val routing = classifier.classify(userMessage)
        emit(KernelStreamEvent.Routing(routing))

        val orchestratorContext = if (routing.needPlanning || routing.needResearch || routing.needCritic) {
            runCatching { orchestrator.run(userMessage, routing) }.getOrNull()
        } else null

        val messages = buildList {
            if (orchestratorContext != null) add(LlmMessage.system("Agent context:\n$orchestratorContext"))
            addAll(conversationHistory)
            add(LlmMessage.user(userMessage))
        }

        val builder = StringBuilder()
        emit(KernelStreamEvent.Start)
        llmProvider.streamComplete(messages, LlmGenerationConfig(maxTokens = 1024)).collect { token ->
            builder.append(token); emit(KernelStreamEvent.Token(token))
        }
        val fullResponse = builder.toString()
        emit(KernelStreamEvent.Done(fullResponse))

        bgScope.launch {
            runCatching {
                memoryService.addEpisodicMemory("User: $userMessage", confidence = 1.0f, tags = listOf("conversation"))
                memoryService.addEpisodicMemory("MOMO: $fullResponse", confidence = 0.9f,
                    source = com.babymomo.data.db.entity.MemorySource.LLM_INFERRED, tags = listOf("conversation", "response"))
                memoryExtractor.extract("User: $userMessage\nMOMO: $fullResponse", confidenceThreshold = 0.6f)
            }
        }
    }

    sealed class KernelStreamEvent {
        data class Routing(val decision: RoutingDecision) : KernelStreamEvent()
        object Start : KernelStreamEvent()
        data class Token(val text: String) : KernelStreamEvent()
        data class Done(val fullResponse: String) : KernelStreamEvent()
    }

    data class KernelResult(
        val content: String = "", val routing: RoutingDecision,
        val tokensIn: Int = 0, val tokensOut: Int = 0, val latencyMs: Long = 0,
        val providerName: String = "", val error: String? = null
    )
}
