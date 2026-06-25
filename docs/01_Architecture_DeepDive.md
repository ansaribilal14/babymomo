# 01 — Architecture Deep Dive

## Module Overview

This document specifies the complete runtime architecture of Babymomo — the loop that processes every user turn, the layer stack that separates concerns, the data-flow paths between subsystems, the error-handling contracts, and the threading model that keeps the UI responsive while LLM inference and tool execution run in the background.

Babymomo is a **single-activity Android app** built with Jetpack Compose, Kotlin coroutines, Hilt dependency injection, and Room + SQLCipher storage. Every user interaction follows one deterministic pipeline — the **Babymomo Loop** — that classifies intent, recalls memory, enriches the system prompt, streams LLM tokens, executes tools in a loop, extracts new memories, and promotes high-value memories into permanent context.

---

## 1. The Babymomo Loop

Every user turn passes through this pipeline. No shortcuts, no bypasses.

```
User Input
   │
   ▼
┌──────────────────────┐
│  RequestClassifier   │  ← classifies intent: Chat / Skill / Agent / Interactive
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  MemoryRecaller      │  ← pulls top-8 memories: cosine(0.40) + graph(0.30)
│                      │    + confidence(0.20) + recency(0.10)
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  WrappedLlmProvider  │  ← assembles system prompt:
│                      │    [CORE SOUL]
│                      │    [PROMOTED MEMORIES — PERMANENT]
│                      │    [RECALLED MEMORIES — THIS TURN]
│                      │    [ACTIVE PROJECTS]
│                      │    [CONTEXT]
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  LlmChain            │  ← LiteRT → Remote → Mock (never throws)
└──────────┬───────────┘
           │  ┌────────────────────────────────────────┐
           │  │  Tool Loop (repeats while ToolCall     │
           │  │  chunks appear):                       │
           │  │    ToolRegistry.execute(name, input)   │
           │  │    → append ToolResult to message list │
           │  │    → continue LLM stream               │
           │  └────────────────────────────────────────┘
           │
           ▼
┌──────────────────────┐
│  MemoryExtractor     │  ← LLM-powered entity + relation + fact extraction
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  MemoryService       │  ← persists bi-temporal memories + updates graph
│                      │    + increments hitCount on recalled memories
└──────────┬───────────┘
           │  hitCount >= 5?
           ▼
┌──────────────────────┐
│  MemoryPromoter      │  ← promotes memory into permanent system prompt
└──────────────────────┘
           │
           ▼
Response (streamed, with [m_abc] citations)
```

### Sequence Contract

```kotlin
// Pseudocode for MomoKernel.streamProcess()
suspend fun streamProcess(messages: List<Message>): Flow<KernelOutput> = flow {
    val lastUserMsg = messages.last { it.role == "user" }.content

    // STEP 1: Classify
    val route = requestClassifier.classify(lastUserMsg)

    // STEP 2: Recall (happens inside WrappedLlmProvider.buildSystemPrompt)
    // STEP 3: Assemble system prompt (also inside WrappedLlmProvider)

    // STEP 4+5: Stream + tool loop
    val toolDefs = toolRegistry.getAvailableTools().map { Tool(it.name, it.description, it.parameters) }
    val chunkFlow = llmProvider.streamChat(messages, toolDefs)

    val fullResponse = StringBuilder()
    chunkFlow.collect { chunk ->
        when (chunk) {
            is LlmChunk.Token -> {
                fullResponse.append(chunk.text)
                emit(KernelOutput.Token(chunk.text))
            }
            is LlmChunk.ToolCall -> {
                val result = toolRegistry.execute(chunk.name, chunk.input.toString())
                emit(KernelOutput.ToolUsed(chunk.name, result))
                // LLM continues after tool result is appended
            }
            is LlmChunk.Done -> {
                // STEP 6: Extract memories (fire-and-forget, never blocks response)
                try { memoryService.processConversationTurn(lastUserMsg, fullResponse.toString()) }
                catch (_: Exception) { /* never block the response */ }
                emit(KernelOutput.Done(route.name))
            }
            is LlmChunk.Error -> emit(KernelOutput.Error(chunk.message))
            is LlmChunk.ToolResult -> { /* handled internally by provider */ }
        }
    }
}
```

---

## 2. Layer Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     UI Layer (Jetpack Compose)                  │
│  ChatScreen · MemoryScreen · ProjectsScreen · ModelsScreen     │
│  SettingsScreen · HeartbeatScreen · TerminalScreen              │
│  InteractiveScreen · McpScreen                                  │
├─────────────────────────────────────────────────────────────────┤
│                     ViewModel Layer (Hilt-injected)             │
│  ChatViewModel · MemoryViewModel · ProjectsViewModel            │
│  ModelsViewModel · SettingsViewModel · HeartbeatViewModel       │
│  TerminalViewModel · McpViewModel                               │
│  Each holds StateFlow<UiState>, no mutable state in composables│
├─────────────────────────────────────────────────────────────────┤
│                     Kernel Layer                                │
│  MomoKernel (brain stem) · RequestClassifier                    │
│  AgentOrchestrator · SkillRegistry                              │
├────────┬──────────────────┬──────────────────┬──────────────────┤
│ Agent  │  LLM Chain       │  Memory Layer    │  Interactive     │
│ Layer  │                  │                  │  Layer           │
│Planner │  LocalLlmProvider│  MemoryRecaller  │  ScreenParser    │
│Research│  RemoteLlmProv.  │  MemoryExtractor │  ScreenDescriptor│
│Memory  │  MockLlmProvider │  MemoryPromoter  │  Renderer        │
│Critic  │  WrappedLlmProv. │  MemoryGraph     │                  │
│Executor│  LlmChain        │  VectorIndex     │                  │
├────────┴──────────────────┴──────────────────┴──────────────────┤
│                     Execution Layer                             │
│  SkillRegistry · ToolRegistry · McpClient                      │
│  WebSearchTool · NotificationTool · CalendarTool · ShellTool    │
│  MemoryStoreTool · MemoryRecallTool · ShellSkill · CalendarSkill│
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                        │
│  LinuxSandbox · SandboxInstaller · SandboxSession               │
│  ProjectService · ProjectContextProvider · ModelManager          │
├─────────────────────────────────────────────────────────────────┤
│                     Data Layer                                  │
│  Room Database (SQLCipher-encrypted)                            │
│  12 Entities · 12 DAOs · AppDatabase                           │
│  EncryptedSharedPreferences (API keys)                          │
├─────────────────────────────────────────────────────────────────┤
│                     Background Work Layer                       │
│  WorkManager: HeartbeatWorker · MemoryMaintenanceWorker         │
│              ModelDownloadWorker                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Dependency Rule

Dependencies point **downward only**. The UI never imports from the Agent layer directly. The Agent layer never imports from the Data layer directly — it goes through MemoryService / ProjectService. The Data layer has no knowledge of any layer above it.

---

## 3. Data Flow Diagrams

### 3A. Chat Turn — Happy Path

```
User types "What's the weather in Tokyo?"
   │
   ▼ ChatViewModel.sendMessage(text)
   │
   ▼ MomoKernel.streamProcess(messages)
   │
   ├─► RequestClassifier.classify("What's the weather in Tokyo?")
   │      → RouteType.Chat  (no skill/agent keywords detected)
   │
   ├─► WrappedLlmProvider.streamChat(messages, tools)
   │      ├─► buildSystemPrompt()
   │      │      ├─► MemoryRecaller.recall("weather tokyo", topK=8)
   │      │      │      → [MemoryEntity("User travels to Japan frequently", EPISODIC)]
   │      │      ├─► MemoryRecaller.getPromotedMemories()
   │      │      │      → [MemoryEntity("User prefers Celsius", PROCEDURAL, promoted)]
   │      │      └─► ProjectContextProvider.getActiveProjectsContext()
   │      │             → []
   │      │
   │      └─► LlmChain.streamChat(assembledPrompt, messages, tools)
   │             ├─► LocalLlmProvider.isAvailable()? → false (no model downloaded)
   │             ├─► RemoteLlmProvider.isAvailable()? → true (OpenAI key set)
   │             └─► RemoteLlmProvider.streamChat(...) → Flow<LlmChunk>
   │                    ├─► LlmChunk.Token("I'll") → emit KernelOutput.Token
   │                    ├─► LlmChunk.Token(" check") → emit KernelOutput.Token
   │                    ├─► LlmChunk.ToolCall("web_search", {query: "weather Tokyo"})
   │                    │      → ToolRegistry.execute("web_search", ...)
   │                    │      → "Partly cloudy, 18°C in Tokyo"
   │                    │      → append ToolResult, continue stream
   │                    ├─► LlmChunk.Token("Currently 18°C...")
   │                    └─► LlmChunk.Done
   │
   ├─► MemoryService.processConversationTurn(userMsg, fullResponse)
   │      ├─► MemoryExtractor.extract(userMsg, response)
   │      │      → ExtractionResult(
   │      │           entities=[{name:"Tokyo", type:"PLACE"}],
   │      │           relations=[],
   │      │           memories=[{content:"User asked about Tokyo weather", type:"EPISODIC", confidence:0.6}]
   │      │         )
   │      ├─► MemoryGraph.findOrCreateEntity("Tokyo", "PLACE")
   │      └─► storeMemory("User asked about Tokyo weather", EPISODIC, 0.6)
   │             → VectorIndex.index(memoryId, content)
   │             → MemoryPromoter.checkAndPromote(memoryId) → hitCount=0, skip
   │
   └─► emit KernelOutput.Done("Chat")
          │
          ▼ ChatViewModel collects flow, updates StateFlow
          │
          ▼ Composable recomposes with new message + streaming tokens
```

### 3B. Chat Turn — All Providers Fail

```
LlmChain.streamChat(...)
   ├─► LocalLlmProvider.isAvailable()? → false
   ├─► RemoteLlmProvider.isAvailable()? → true
   │      └─► streamChat throws IOException
   │             → catch, fall through
   └─► MockLlmProvider.streamChat(...)
          → deterministic hash-based response (always succeeds)
```

### 3C. Memory Promotion Flow

```
MemoryRecaller.recall("japan travel", topK=8)
   → MemoryEntity(id="mem_abc", content="User visits Japan annually", hitCount=4)
   → memoryDao.incrementHitCount("mem_abc") → hitCount becomes 5
   → MemoryPromoter.checkAndPromote("mem_abc")
      → hitCount(5) >= PROMOTION_THRESHOLD(5) → true
      → isInSystemPrompt == false → true
      → memoryDao.promote("mem_abc", now) → isInSystemPrompt=true, validTo=now
      → Future calls: this memory appears in [PROMOTED MEMORIES] section
```

---

## 4. Error Handling Strategy

### 4A. Principle: Never Crash, Always Degrade Gracefully

| Layer | Error Scenario | Recovery |
|-------|---------------|----------|
| LLM | Local model OOM | Skip to next provider in LlmChain |
| LLM | Remote API 429/500 | Catch exception, fall to MockProvider |
| LLM | Network offline | RemoteLlmProvider.isAvailable()=false, skip |
| LLM | All providers fail | MockLlmProvider always returns deterministic response |
| Memory | Extraction LLM call fails | Return empty ExtractionResult, no memories stored |
| Memory | VectorIndex corrupt | Fall back to text search only (MemoryDao.search) |
| Memory | Room write fails | Log error, skip memory persistence, response still delivered |
| Tools | Tool execution throws | Return "Tool error (name): message" as string result |
| Tools | Unknown tool name | Return "Unknown tool: name" |
| Sandbox | Sandbox not installed | ShellTool returns "Sandbox not ready" message |
| Sandbox | Command timeout (30s) | Kill process, return "Command timed out" |
| Heartbeat | LLM call fails | Default to "SILENT", log the run |
| UI | Compose crash | Caught by global exception handler, show error state |

### 4B. Error Sealed Classes

```kotlin
sealed class KernelOutput {
    data class Token(val text: String) : KernelOutput()
    data class ToolUsed(val toolName: String, val result: String) : KernelOutput()
    data class Done(val routingReason: String) : KernelOutput()
    data class Error(val message: String) : KernelOutput()
}

sealed class LlmChunk {
    data class Token(val text: String) : LlmChunk()
    data class ToolCall(val id: String, val name: String, val input: JsonObject) : LlmChunk()
    data class ToolResult(val callId: String, val result: String) : LlmChunk()
    object Done : LlmChunk()
    data class Error(val message: String) : LlmChunk()
}
```

### 4C. Tool Loop Error Contract

When a tool execution fails:
1. The error message string is returned as the tool result
2. The LLM receives this as a `tool_result` message
3. The LLM decides whether to retry, use a different tool, or respond to the user directly
4. The tool loop has a **maximum iteration guard of 10** to prevent infinite loops

```kotlin
// Tool loop guard constant
companion object {
    const val MAX_TOOL_ITERATIONS = 10
}
```

---

## 5. Threading Model

### 5A. Coroutine Dispatchers

| Operation | Dispatcher | Rationale |
|-----------|-----------|-----------|
| LLM streaming (network) | `Dispatchers.IO` | Network calls, SSE parsing |
| LLM streaming (local) | `Dispatchers.Default` | CPU-bound LiteRT inference |
| Memory extraction | `Dispatchers.IO` | LLM call + DB writes |
| Vector search | `Dispatchers.Default` | CPU-bound cosine similarity |
| Room DAO calls | `Dispatchers.IO` | Room's default, disk I/O |
| Tool execution | `Dispatchers.IO` | Network, content resolver, process |
| UI state updates | `Dispatchers.Main` | Compose observation |
| HeartbeatWorker | Worker's own thread | WorkManager-managed |

### 5B. Structured Concurrency Boundaries

```kotlin
// ChatViewModel: scope tied to ViewModel lifecycle
class ChatViewModel @Inject constructor(...) : ViewModel() {
    private val scope = viewModelScope  // Dispatchers.Main.immediate

    fun sendMessage(text: String) {
        scope.launch {
            // Switches to IO for LLM call
            kernel.streamProcess(messages)
                .flowOn(Dispatchers.IO)      // upstream runs on IO
                .collect { output ->          // collected on Main
                    _uiState.update { ... }   // safe to update Compose state
                }
        }
    }
}
```

### 5C. Mutual Exclusion

| Resource | Concurrency Strategy |
|----------|---------------------|
| Room Database | Room handles its own locking; DAOs are `suspend` |
| MemoryService.processConversationTurn | Called sequentially per turn (no parallel extraction) |
| ToolRegistry.execute | Each tool is stateless; safe to call concurrently |
| LinuxSandbox.execute | **Mutex required** — single proot process, one command at a time |
| VectorIndex | Read-only during search; writes are sequential in MemoryService |
| LlmProvider instances | Stateful (SSE connection); only one stream per provider at a time |

### 5D. Sandbox Mutex

```kotlin
@Singleton
class LinuxSandbox @Inject constructor(...) {
    private val executionMutex = Mutex()

    suspend fun executeSuspend(command: String): String = executionMutex.withLock {
        // Only one command at a time against the proot process
        execute(command)
    }
}
```

---

## 6. Package Dependency Graph

```
com.babymomo.app
├── BabymomoApp.kt ─────► (Hilt entry, WorkManager init)
├── MainActivity.kt ─────► (NavHost, no business logic)
│
├── core/
│   ├── kernel/ ─────────► llm, memory, tools, agents, skills
│   │   ├── MomoKernel
│   │   └── RequestClassifier
│   ├── llm/ ────────────► (no upward deps; model/ sub-package only)
│   │   ├── LlmProvider (interface)
│   │   ├── LlmChain ────► LocalLlmProvider, RemoteLlmProvider, MockLlmProvider
│   │   ├── WrappedLlmProvider ──► LlmChain, MemoryRecaller, ProjectContextProvider
│   │   └── model/ (LlmChunk, Message, Tool)
│   ├── memory/ ─────────► llm (for extraction), data.db.dao
│   ├── agents/ ─────────► llm, skills
│   ├── skills/ ─────────► llm (standalone)
│   ├── tools/ ──────────► sandbox, mcp
│   ├── mcp/ ────────────► data.db.dao
│   ├── interactive/ ────► (standalone data classes + parser)
│   ├── sandbox/ ────────► (standalone, Android Context only)
│   └── projects/ ───────► data.db.dao
│
├── data/db/ ────────────► (no upward deps)
│   ├── AppDatabase
│   ├── entities/ (12 Room entities)
│   └── dao/ (12 DAOs)
│
├── ui/ ─────────────────► core.kernel (via ViewModels only)
│   ├── theme/
│   ├── nav/
│   └── screens/ (10 screen packages)
│
├── work/ ───────────────► core.llm, core.memory
└── model/ ──────────────► data.db.dao
```

---

## 7. Initialization Sequence

```
App Launch
   │
   ▼ BabymomoApp.onCreate()
   │
   ├─► Hilt injects all @Singleton dependencies
   ├─► WorkManager.enqueueUniquePeriodicWork(
   │        "heartbeat",
   │        ExistingPeriodicWorkPolicy.KEEP,
   │        PeriodicWorkRequest(HeartbeatWorker::class, 30, MINUTES)
   │    )
   ├─► WorkManager.enqueueUniquePeriodicWork(
   │        "memory_maintenance",
   │        ExistingPeriodicWorkPolicy.KEEP,
   │        PeriodicWorkRequest(MemoryMaintenanceWorker::class, 6, HOURS)
   │    )
   └─► MainActivity.launch()
          │
          ▼ setContent { BabymomoTheme { NavHost(...) } }
```

---

## 8. Test Scenarios

### 8A. Architecture Integration Tests

| Test | Description | Expected |
|------|------------|----------|
| `loop_completesFullCycle` | Send "hello", verify: classify → recall → stream → extract → done | KernelOutput.Done emitted |
| `loop_fallsBackToMock` | Disable local + remote, send message | MockProvider response delivered |
| `loop_toolLoopExecutes` | Send message that triggers web_search tool | KernelOutput.ToolUsed emitted, then Done |
| `loop_toolLoopMaxIterations` | Mock LLM that always returns ToolCall | Loop stops after 10 iterations |
| `loop_memoryExtractedAfterResponse` | Send message, wait for Done, check MemoryDao | New memory entity persisted |
| `loop_memoryPromotedAfter5Hits` | Recall same memory 5 times | isInSystemPrompt=true |

### 8B. Error Path Tests

| Test | Description | Expected |
|------|------------|----------|
| `error_remoteProviderCrashes` | RemoteLlmProvider throws mid-stream | Falls to MockProvider |
| `error_memoryExtractionFails` | LLM returns malformed JSON | Empty ExtractionResult, no crash |
| `error_toolExecutionThrows` | Tool.execute() throws RuntimeException | Error string returned to LLM |
| `error_sandboxNotReady` | ShellTool called before sandbox install | "Sandbox not ready" message |

### 8C. Threading Tests

| Test | Description | Expected |
|------|------------|----------|
| `thread_noMainThreadBlocking` | streamProcess runs 1000 tokens | All emissions on Main, processing on IO |
| `thread_concurrentToolExecution` | Two tools called simultaneously | Both complete, no deadlock |
| `thread_sandboxMutex` | Two shell commands submitted concurrently | Serialized, no interleaved output |
