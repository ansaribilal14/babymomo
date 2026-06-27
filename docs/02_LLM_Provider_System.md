# 02 — LLM Provider System

## Module Overview

The LLM Provider System is the inference backbone of Babymomo. It defines the `LlmProvider` interface that all LLM backends implement, the `LlmChain` that provides transparent fallback across providers, four concrete provider implementations, and the `WrappedLlmProvider` decorator that enriches every system prompt with memories and project context. All providers support real SSE streaming — no simulated delays.

**Key Principle:** The chain never throws. If every upstream provider fails, the MockLlmProvider always returns a deterministic response. The user always gets an answer.

---

## 1. LlmProvider Interface

```kotlin
package com.babymomo.app.core.llm

interface LlmProvider {
    /**
     * Stream a chat completion. Emits LlmChunk.Token for each token,
     * LlmChunk.ToolCall when the model wants to invoke a tool,
     * and LlmChunk.Done when the response is complete.
     */
    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk>

    /**
     * Single blocking completion. Used by:
     * - MemoryExtractor (extraction prompt)
     * - RequestClassifier (classification prompt)
     * - HeartbeatWorker (background check prompt)
     * - Agent.process() (agent reasoning)
     */
    suspend fun complete(prompt: String): String

    /** Whether this provider can be used right now. */
    fun isAvailable(): Boolean

    /** Human-readable name for logging and UI display. */
    fun providerName(): String
}
```

### LlmChunk Sealed Class

```kotlin
sealed class LlmChunk {
    /** A single token of the response text. */
    data class Token(val text: String) : LlmChunk()

    /** The model wants to invoke a tool. */
    data class ToolCall(
        val id: String,         // unique call ID from the LLM
        val name: String,       // tool name, e.g. "web_search"
        val input: JsonObject   // tool input parameters
    ) : LlmChunk()

    /** Result from a tool execution, fed back to the model. */
    data class ToolResult(
        val callId: String,     // matches ToolCall.id
        val result: String      // tool execution result
    ) : LlmChunk()

    /** Response is complete. No more chunks. */
    object Done : LlmChunk()

    /** An error occurred during streaming. */
    data class Error(val message: String) : LlmChunk()
}
```

### Message & Tool Model Classes

```kotlin
data class Message(
    val role: String,       // "system" | "user" | "assistant" | "tool"
    val content: String,
    val toolCallId: String? = null,   // for role="tool", matches ToolCall.id
    val toolCalls: List<ToolCall>? = null  // for role="assistant" with tool_use
)

data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonObject   // JSON Schema of tool parameters
)

data class ToolCall(
    val id: String,
    val name: String,
    val input: JsonObject
)
```

---

## 2. LlmChain — Fallback Orchestrator

### Priority Order

```
Priority 1: LocalLlmProvider  (LiteRT, if model downloaded + active)
Priority 2: RemoteLlmProvider (OpenAI / NVIDIA NIM / OpenRouter — first configured)
Priority 3: MockLlmProvider   (always available, deterministic hash-based)
```

### Implementation

```kotlin
@Singleton
class LlmChain @Inject constructor(
    private val localProvider: LocalLlmProvider,
    private val remoteProvider: RemoteLlmProvider,
    private val mockProvider: MockLlmProvider
) {
    fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool> = emptyList()
    ): Flow<LlmChunk> = flow {
        // Priority 1: Local (LiteRT)
        if (localProvider.isAvailable()) {
            emitAll(localProvider.streamChat(systemPrompt, messages, tools))
            return@flow
        }

        // Priority 2: Remote (OpenAI / NVIDIA NIM / OpenRouter)
        if (remoteProvider.isAvailable()) {
            try {
                emitAll(remoteProvider.streamChat(systemPrompt, messages, tools))
                return@flow
            } catch (_: Exception) {
                // Fall through to mock — never crash
            }
        }

        // Priority 3: Mock (always available)
        emitAll(mockProvider.streamChat(systemPrompt, messages, tools))
    }

    suspend fun complete(prompt: String): String {
        if (localProvider.isAvailable()) {
            val result = localProvider.complete(prompt)
            if (result.isNotEmpty()) return result
        }

        if (remoteProvider.isAvailable()) {
            try {
                return remoteProvider.complete(prompt)
            } catch (_: Exception) { }
        }

        return mockProvider.complete(prompt)
    }
}
```

### Fallback Decision Tree

```
streamChat called
   │
   ├─► localProvider.isAvailable() == true?
   │      YES → emitAll(localProvider.streamChat(...))
   │             If stream throws → falls through (only for complete())
   │             If stream completes → return
   │
   ├─► remoteProvider.isAvailable() == true?
   │      YES → try emitAll(remoteProvider.streamChat(...))
   │             If throws → fall through to mock
   │             If completes → return
   │
   └─► emitAll(mockProvider.streamChat(...))
          Always succeeds
```

---

## 3. LocalLlmProvider — On-Device LiteRT

### Specification

| Property | Value |
|----------|-------|
| Engine | Google LiteRT (TensorFlow Lite for LLMs) |
| Models | Gemma 2B IT (~1.5GB), Phi-3 Mini 3.8B (~2.4GB) |
| Format | `.bin` (LiteRT flatbuffer) |
| Thread affinity | `Dispatchers.Default` (CPU-bound) |
| GPU | Optional GPU delegate via `litert-gpu` |
| Max context | 2048 tokens (Gemma 2B), 4096 tokens (Phi-3 Mini) |

### Implementation Sketch

```kotlin
@Singleton
class LocalLlmProvider @Inject constructor(
    private val modelManager: ModelManager
) : LlmProvider {

    private var interpreter: LiteRtInterpreter? = null

    override fun isAvailable(): Boolean {
        return interpreter != null && modelManager.hasActiveModel()
    }

    override fun providerName(): String = "LiteRT"

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        val interp = interpreter ?: run {
            emit(LlmChunk.Error("No local model loaded"))
            emit(LlmChunk.Done)
            return@flow
        }

        val formattedPrompt = formatChatML(systemPrompt, messages)
        val tokenIds = tokenize(formattedPrompt)

        // Autoregressive generation loop
        for (i in 0 until MAX_TOKENS) {
            val nextToken = interp.runStep(tokenIds)
            tokenIds.add(nextToken)

            val text = detokenize(listOf(nextToken))

            // Check for tool call patterns in accumulated text
            val accumulated = detokenize(tokenIds)
            val toolCall = parseToolCall(accumulated)
            if (toolCall != null) {
                emit(LlmChunk.ToolCall(toolCall.id, toolCall.name, toolCall.input))
            } else if (isEndOfSequence(nextToken)) {
                emit(LlmChunk.Done)
                break
            } else {
                emit(LlmChunk.Token(text))
            }
        }
        emit(LlmChunk.Done)
    }.flowOn(Dispatchers.Default)

    override suspend fun complete(prompt: String): String {
        if (!isAvailable()) return ""
        return try {
            val tokenIds = tokenize(prompt)
            for (i in 0 until MAX_TOKENS) {
                val nextToken = interpreter!!.runStep(tokenIds)
                tokenIds.add(nextToken)
                if (isEndOfSequence(nextToken)) break
            }
            detokenize(tokenIds)
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val MAX_TOKENS = 512
    }
}
```

### Model Loading

```kotlin
// Called when user activates a model from ModelsScreen
fun loadModel(modelPath: String) {
    val options = LiteRtInterpreter.Options()
        .setNumThreads(4)
    // GPU delegate if available
    if (GpuDelegate.isAvailableOnDevice()) {
        options.addDelegate(GpuDelegate())
    }
    interpreter = LiteRtInterpreter.create(modelPath, options)
}

fun unloadModel() {
    interpreter?.close()
    interpreter = null
}
```

---

## 4. RemoteLlmProvider — OpenAI-Compatible SSE

### Supported Providers

| Provider | Base URL | Auth Header | Notes |
|----------|---------|------------|-------|
| OpenAI | `https://api.openai.com` | `Bearer sk-...` | Standard GPT models |
| NVIDIA NIM | `https://integrate.api.nvidia.com` | `Bearer nvapi-...` | OpenAI-compatible |
| OpenRouter | `https://openrouter.ai/api` | `Bearer sk-or-...` | Multi-model routing |

All three share the **OpenAI `/v1/chat/completions`** API shape. The user configures which provider(s) to use, along with API key and model string, in Settings.

### SSE Streaming Protocol

```
POST /v1/chat/completions
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "model": "<model_string>",
  "messages": [...],
  "stream": true,
  "tools": [...]
}
```

**SSE Response:**

```
data: {"choices":[{"delta":{"content":"I"}}]}
data: {"choices":[{"delta":{"content":"'ll"}}]}
data: {"choices":[{"delta":{"content":" check"}}]}
data: {"choices":[{"delta":{"tool_calls":[{"id":"call_abc","function":{"name":"web_search","arguments":"{\"query\":\"tokyo weather\"}"}}]}}]}
data: {"choices":[{"delta":{},"finish_reason":"stop"}]}
data: [DONE]
```

### Implementation

```kotlin
@Singleton
class RemoteLlmProvider @Inject constructor(
    private val settingsDao: SettingsDao,
    private val encryptedPrefs: EncryptedSharedPreferences
) : LlmProvider {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(SSE)
    }

    override fun isAvailable(): Boolean {
        // Check if any remote provider has an API key configured
        return getApiKey() != null
    }

    override fun providerName(): String = "Remote"

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        val apiKey = getApiKey() ?: run {
            emit(LlmChunk.Error("No API key configured"))
            emit(LlmChunk.Done)
            return@flow
        }
        val baseUrl = getBaseUrl()
        val model = getModelString()

        val request = buildOpenAIRequest(systemPrompt, messages, tools, model)

        client.sse("$baseUrl/v1/chat/completions", apiKey, request).collect { event ->
            when (event) {
                is SSEEvent.Data -> {
                    val chunk = parseSSEChunk(event.data)
                    chunk?.let { emit(it) }
                }
                is SSEEvent.Done -> emit(LlmChunk.Done)
                is SSEEvent.Error -> emit(LlmChunk.Error(event.message))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String): String {
        if (!isAvailable()) return ""
        return try {
            val apiKey = getApiKey()!!
            val baseUrl = getBaseUrl()
            val model = getModelString()

            val response = client.post("$baseUrl/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(buildString {
                    append("""{"model":"$model","messages":[{"role":"user","content":${Json.encodeToString(prompt)}}],"stream":false}""")
                })
            }
            parseCompletionResponse(response.bodyAsText())
        } catch (e: Exception) {
            ""
        }
    }

    private fun getApiKey(): String? {
        // Read from EncryptedSharedPreferences
        return encryptedPrefs.getString("remote_api_key", null)
    }

    private fun getBaseUrl(): String {
        return encryptedPrefs.getString("remote_base_url", "https://api.openai.com")!!
    }

    private fun getModelString(): String {
        return encryptedPrefs.getString("remote_model", "gpt-4o-mini")!!
    }
}
```

### SSE Chunk Parsing

```kotlin
fun parseSSEChunk(data: String): LlmChunk? {
    if (data == "[DONE]") return LlmChunk.Done

    val json = Json.parseToJsonElement(data).jsonObject
    val choice = json["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val delta = choice["delta"]?.jsonObject ?: return null

    // Text content
    val content = delta["content"]?.toString()?.trim('"') ?: ""
    if (content.isNotEmpty()) return LlmChunk.Token(content)

    // Tool calls
    val toolCalls = delta["tool_calls"]?.jsonArray
    if (toolCalls != null && toolCalls.isNotEmpty()) {
        val tc = toolCalls.first().jsonObject
        val id = tc["id"]?.toString()?.trim('"') ?: "call_${System.currentTimeMillis()}"
        val function = tc["function"]?.jsonObject ?: return null
        val name = function["name"]?.toString()?.trim('"') ?: return null
        val args = function["arguments"]?.toString()?.trim('"') ?: "{}"
        val input = Json.parseToJsonElement(args).jsonObject
        return LlmChunk.ToolCall(id, name, input)
    }

    // Finish reason
    val finishReason = choice["finish_reason"]?.toString()?.trim('"')
    if (finishReason == "stop") return LlmChunk.Done

    return null
}
```

---

## 5. MockLlmProvider — Deterministic Dev/CI

### Design

The MockLlmProvider produces deterministic, hash-based responses. It never calls any external service. It's used in three scenarios:

1. **Development** — no API keys needed, instant feedback
2. **CI** — tests run without network access
3. **Offline fallback** — when all other providers are unavailable

### Implementation

```kotlin
@Singleton
class MockLlmProvider @Inject constructor() : LlmProvider {

    override fun isAvailable(): Boolean = true  // Always available
    override fun providerName(): String = "Mock"

    override fun streamChat(
        systemPrompt: String,
        messages: List<Message>,
        tools: List<Tool>
    ): Flow<LlmChunk> = flow {
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: "Hello"
        val response = generateDeterministicResponse(lastUserMsg)

        // Simulate word-by-word streaming with 30ms delay
        val words = response.split(" ")
        for (word in words) {
            emit(LlmChunk.Token(if (word == words.first()) word else " $word"))
            delay(30)
        }
        emit(LlmChunk.Done)
    }

    override suspend fun complete(prompt: String): String {
        return generateDeterministicResponse(prompt)
    }

    private fun generateDeterministicResponse(input: String): String {
        val hash = abs(input.hashCode())
        return when (hash % 5) {
            0 -> "I understand your question about \"$input\". Let me think about that."
            1 -> "That's an interesting point. Based on what I know, I'd say the answer involves several factors."
            2 -> "I recall we discussed something similar before. Here's what I think about \"$input\"."
            3 -> "Great question! Let me break this down for you step by step."
            else -> "I've noted your question about \"$input\" and I'll remember this for our future conversations."
        }
    }
}
```

---

## 6. WrappedLlmProvider — The Decorator

### Design

`WrappedLlmProvider` is the **decorator** that sits between `MomoKernel` and `LlmChain`. Before every LLM call, it assembles the system prompt by injecting recalled memories, promoted memories, and project context. This is Kai's pattern — the LLM always receives the full context of who the user is and what it knows.

### System Prompt Assembly

```
[CORE SOUL]
{user-defined personality / base instructions}

[PROMOTED MEMORIES — PERMANENT]
- User prefers Celsius for temperature [promoted, accessed 12 times]
- User is a software engineer [promoted, accessed 8 times]
- User's cat is named Mochi [promoted, accessed 7 times]

[RECALLED MEMORIES — THIS TURN]
- User visited Japan last March [m_a1b2c3d4]
- User asked about Tokyo weather previously [m_e5f6g7h8]

[ACTIVE PROJECTS]
- Home Renovation: Planning kitchen remodel, need contractor quotes
- Rust Learning: Working through the book, chapter 5

[CONTEXT]
Current date: 2026-03-04
Current time: 14:32:05
```

### Implementation

```kotlin
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
        val systemPrompt = buildSystemPrompt(messages)
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
                sb.appendLine("- ${mem.content} [promoted, accessed ${mem.hitCount} times]")
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
        const val DEFAULT_SOUL = """You are Babymomo, a private AI companion that lives on the user's device. ..."""
    }
}
```

### Token Budget

The system prompt is limited to **4096 tokens** to leave room for conversation and response:

| Section | Max Tokens | Strategy |
|---------|-----------|----------|
| CORE SOUL | 256 | User-defined, truncated if too long |
| PROMOTED MEMORIES | 1024 | Truncate oldest promoted memories |
| RECALLED MEMORIES | 1024 | topK=8, truncate each to 128 tokens |
| ACTIVE PROJECTS | 512 | Truncate to 3 projects |
| CONTEXT | 64 | Date/time, always fits |
| **Total** | **2872** | Leaves 1224+ tokens for conversation |

---

## 7. Streaming Protocol — End to End

```
┌───────────┐    HTTP SSE     ┌───────────────┐    Flow<LlmChunk>    ┌─────────────┐
│ OpenAI    │ ─────────────► │ RemoteLlm     │ ──────────────────► │ LlmChain    │
│ Server    │  data: {...}   │ Provider      │  LlmChunk.Token     │             │
│           │  data: [DONE]  │               │  LlmChunk.Done      │             │
└───────────┘                └───────────────┘                      └──────┬──────┘
                                                                         │
                                     Flow<LlmChunk>                      │
                           ┌──────────────────────┐                       │
                           │ WrappedLlmProvider   │ ◄─────────────────────┘
                           │ (adds system prompt) │
                           └──────────┬───────────┘
                                      │
                            Flow<LlmChunk>
                                      │
                           ┌──────────▼───────────┐
                           │ MomoKernel           │
                           │ (tool loop, memory)  │
                           └──────────┬───────────┘
                                      │
                          Flow<KernelOutput>
                                      │
                           ┌──────────▼───────────┐
                           │ ChatViewModel        │
                           │ _uiState.update { }  │
                           └──────────┬───────────┘
                                      │
                         StateFlow<UiState>
                                      │
                           ┌──────────▼───────────┐
                           │ ChatScreen           │
                           │ (Compose)            │
                           └──────────────────────┘
```

---

## 8. Error Handling

| Scenario | Provider | Recovery |
|----------|----------|----------|
| Local model OOM | LocalLlmProvider | `isAvailable()` returns false, chain skips |
| Local model not downloaded | LocalLlmProvider | `isAvailable()` returns false |
| API key not configured | RemoteLlmProvider | `isAvailable()` returns false |
| Network timeout (30s) | RemoteLlmProvider | `try/catch` in LlmChain, fall to Mock |
| Rate limit (429) | RemoteLlmProvider | `try/catch`, fall to Mock |
| Server error (5xx) | RemoteLlmProvider | `try/catch`, fall to Mock |
| Malformed SSE chunk | RemoteLlmProvider | Skip chunk, continue stream |
| All providers fail | LlmChain | MockLlmProvider always returns |
| System prompt too long | WrappedLlmProvider | Truncate sections per budget |

---

## 9. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `chain_localFirst` | Local available, remote configured | LocalLlmProvider.streamChat called |
| `chain_remoteFallback` | Local unavailable, remote available | RemoteLlmProvider.streamChat called |
| `chain_mockLastResort` | Both unavailable | MockLlmProvider.streamChat called |
| `chain_remoteErrorFallsToMock` | Remote throws IOException | MockProvider used, no crash |
| `mock_deterministic` | Same input twice | Same output both times |
| `mock_alwaysAvailable` | Check isAvailable | Always true |
| `wrapped_assemblesSystemPrompt` | Send message with memories | Prompt contains all 5 sections |
| `wrapped_citationFormat` | Recalled memory with id | Contains `[m_abc12345]` |
| `wrapped_tokenBudget` | 20 promoted memories | Truncated to fit 1024 tokens |
| `remote_sseParsing` | Mock SSE data | Correct LlmChunk emissions |
| `remote_toolCallParsing` | SSE with tool_calls | LlmChunk.ToolCall emitted |
| `local_modelNotLoaded` | No active model | isAvailable()=false, chain skips |
| `complete_localFirst` | Local available | Local complete() result returned |
