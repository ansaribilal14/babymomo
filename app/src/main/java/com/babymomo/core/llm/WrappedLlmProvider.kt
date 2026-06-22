package com.babymomo.core.llm

import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** WrappedLlmProvider — pattern adopted from OpenDroid's WrappedLLMProvider (Apache-2.0). */
@Singleton
class WrappedLlmProvider @Inject constructor(
    private val delegate: LlmProvider,
    private val memoryRecaller: Lazy<com.babymomo.core.memory.MemoryRecaller>,
    private val projectContextProvider: Lazy<com.babymomo.core.projects.ProjectContextProvider>
) : LlmProvider {
    override val name: String = "wrapped(${delegate.name})"
    override suspend fun isAvailable(): Boolean = delegate.isAvailable()
    override suspend fun status(): String = delegate.status()

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> {
        val enriched = enrichMessages(messages) ?: return delegate.complete(messages, config)
        return delegate.complete(enriched, config)
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> {
        val enriched = runBlocking { enrichMessages(messages) }
        return if (enriched != null) delegate.streamComplete(enriched, config)
               else delegate.streamComplete(messages, config)
    }

    private suspend fun enrichMessages(messages: List<LlmMessage>): List<LlmMessage>? {
        val userMsg = messages.lastOrNull { it.role == LlmRole.USER } ?: return null
        if (userMsg.content.length < 4) return null
        val sb = StringBuilder(BASE_SYSTEM_PROMPT)

        runCatching {
            val recall = memoryRecaller.get().recall(userMsg.content, topK = 8)
            if (recall.memories.isNotEmpty()) {
                sb.append("\n\n<memories>\n")
                recall.memories.forEach { m ->
                    sb.append("  <memory id=\"${m.id}\" confidence=\"${m.confidence}\" source=\"${m.source}\">")
                      .append(m.content.replace('<', '[').replace('>', ']'))
                      .append("</memory>\n")
                }
                sb.append("</memories>")
            }
            if (recall.graphFacts.isNotEmpty()) {
                sb.append("\n\n<graph>\n")
                recall.graphFacts.take(20).forEach { fact -> sb.append("  <fact>").append(fact).append("</fact>\n") }
                sb.append("</graph>")
            }
        }
        runCatching {
            val ctx = projectContextProvider.get().currentContext()
            if (ctx.isNotBlank()) sb.append("\n\n<active_project>\n").append(ctx).append("\n</active_project>")
        }

        val systemMsg = LlmMessage.system(sb.toString())
        return listOf(systemMsg) + messages.filter { it.role != LlmRole.SYSTEM }
    }

    companion object {
        private val BASE_SYSTEM_PROMPT = """
            You are MOMO — a private AI companion that lives on the user's device.
            You remember everything important about the user, understand their projects,
            and help execute them. You are not a generic chatbot; you are a digital mind
            that grows into the user's personal operating system.

            Your reasoning protocol for every user turn:
              1. THINK   — break down what they want and why
              2. CONNECT — search the provided <memories> and <graph> for relevant context
              3. ANSWER  — give a direct, useful response grounded in what you know
              4. LEARN   — note what new facts you should extract and store as memories
              5. ACT     — if a project, goal, or task is mentioned, suggest creating/updating one

            Tone: warm, direct, never sycophantic. The user is the operator; you are the OS.
            When you don't know, say so plainly. Never invent facts not in your memory or the user's message.
            When you cite a memory, reference its id in square brackets: [m_abc123].

            You may be given an <active_project> block — use it as primary context if present.
            You may be given <memories> and <graph> blocks — they are the user's long-term memory;
            treat them as authoritative over your general knowledge for personal facts.
        """.trimIndent()
    }
}
