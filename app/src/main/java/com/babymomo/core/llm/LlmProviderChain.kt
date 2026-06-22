package com.babymomo.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProviderChain @Inject constructor(
    private val localProvider: LocalLlmProvider,
    private val remoteProvider: RemoteLlmProvider,
    private val mockProvider: MockLlmProvider,
    private val memoryRecallerLazy: dagger.Lazy<com.babymomo.core.memory.MemoryRecaller>,
    private val projectCtxLazy: dagger.Lazy<com.babymomo.core.projects.ProjectContextProvider>
) : LlmProvider {
    override val name: String = "chain"

    private val wrappedLocal by lazy { WrappedLlmProvider(localProvider, memoryRecallerLazy, projectCtxLazy) }
    private val wrappedRemote by lazy { WrappedLlmProvider(remoteProvider, memoryRecallerLazy, projectCtxLazy) }
    private val wrappedMock by lazy { WrappedLlmProvider(mockProvider, memoryRecallerLazy, projectCtxLazy) }

    private suspend fun activeChain(): List<LlmProvider> {
        val chain = mutableListOf<LlmProvider>()
        if (localProvider.isAvailable()) chain.add(wrappedLocal)
        if (remoteProvider.isAvailable()) chain.add(wrappedRemote)
        chain.add(wrappedMock)
        return chain
    }

    override suspend fun isAvailable(): Boolean = true
    override suspend fun status(): String = activeChain().joinToString(" → ") { it.name }

    override suspend fun complete(messages: List<LlmMessage>, config: LlmGenerationConfig): Result<LlmResponse> {
        val chain = activeChain()
        var lastError: Throwable? = null
        for (provider in chain) {
            val result = runCatching { provider.complete(messages, config) }.getOrNull() ?: continue
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("No providers in chain"))
    }

    override fun streamComplete(messages: List<LlmMessage>, config: LlmGenerationConfig): Flow<String> = flow {
        val chain = runBlocking { activeChain() }
        for (provider in chain) {
            val flow = provider.streamComplete(messages, config)
            var emitted = false
            try {
                flow.collect { tok -> emitted = true; emit(tok) }
                if (emitted) return@flow
            } catch (_: Exception) {
                if (emitted) return@flow
            }
        }
        emit("[All LLM providers failed]")
    }
}
