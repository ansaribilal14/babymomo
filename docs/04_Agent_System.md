# 04 — Agent System

## Module Overview

The Agent System provides Babymomo with specialized reasoning capabilities. When the `RequestClassifier` routes a turn to the `Agent` path, the `AgentOrchestrator` coordinates one or more of five specialist agents — Planner, Researcher, Memory, Critic, and Executor — to handle complex, multi-step tasks. Simple tasks go directly to the Executor; moderate tasks get a plan first; complex tasks go through the full research → plan → execute → critique pipeline.

**Key Principle:** Agents are composable. The orchestrator decides which agents to invoke and in what order based on task complexity. Each agent is a standalone `Agent` interface implementation that receives messages and returns a string result.

---

## 1. Agent Interface

```kotlin
interface Agent {
    val name: String
    val description: String
    suspend fun process(messages: List<Message>): String
}
```

All agents are `@Singleton` classes injected by Hilt. Each agent uses `WrappedLlmProvider.complete()` for its reasoning — a single blocking call that receives the full memory-enriched system prompt.

---

## 2. MomoKernel — The Brain Stem

`MomoKernel` is the entry point for every user turn. It coordinates the full pipeline: classify → stream → tool loop → memory extraction.

```kotlin
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
                    try { memoryService.processConversationTurn(lastUserMsg, fullResponse.toString()) }
                    catch (_: Exception) { }
                    emit(KernelOutput.Done(routingReason))
                }
                is LlmChunk.Error -> emit(KernelOutput.Error(chunk.message))
                is LlmChunk.ToolResult -> { }
            }
        }
    }
}
```

---

## 3. RequestClassifier

### Classification Logic

The `RequestClassifier` examines the user's message to determine the routing path. In v1.0, this uses keyword matching. In future versions, this will use an LLM-based classifier.

```kotlin
@Singleton
class RequestClassifier @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) {
    suspend fun classify(userMessage: String): RouteType {
        val lower = userMessage.lowercase()
        return when {
            // Interactive: visual, screen-based requests
            lower.contains("quiz") || lower.contains("dashboard") ||
            lower.contains("recipe") || lower.contains("game") ||
            lower.contains("interactive") || lower.contains("show me a") ->
                RouteType.Interactive

            // Skill: action-oriented, keyword-matched
            lower.contains("write") || lower.contains("summarize") ||
            lower.contains("search") || lower.contains("calendar") ||
            lower.contains("shell") || lower.contains("run") ->
                RouteType.Skill

            // Agent: complex reasoning, multi-step
            lower.contains("plan") || lower.contains("research") ||
            lower.contains("analyze") || lower.contains("critique") ->
                RouteType.Agent

            // Default: simple chat
            else -> RouteType.Chat
        }
    }

    enum class RouteType {
        Chat, Skill, Agent, Interactive
    }
}
```

### Routing Decision Tree

```
User Message
   │
   ▼ Contains interactive keywords?
   │  (quiz, dashboard, recipe, game, interactive, show me a)
   │
   YES → RouteType.Interactive → InteractiveSkill generates ScreenDescriptor
   │
   NO → Contains skill keywords?
   │     (write, summarize, search, calendar, shell, run)
   │
   YES → RouteType.Skill → ExecutorAgent → SkillRegistry.matchSkill()
   │
   NO → Contains agent keywords?
   │     (plan, research, analyze, critique)
   │
   YES → RouteType.Agent → AgentOrchestrator.orchestrate()
   │
   NO → RouteType.Chat → Direct LLM stream (no agents, no skills)
```

---

## 4. Five Specialist Agents

### 4A. PlannerAgent

**Purpose:** Breaks complex tasks into actionable step-by-step plans.

```kotlin
@Singleton
class PlannerAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "PlannerAgent"
    override val description = "Breaks complex tasks into actionable steps"

    override suspend fun process(messages: List<Message>): String {
        val task = messages.lastOrNull()?.content ?: ""
        val prompt = """You are a planning specialist. Create a clear, step-by-step plan for the following task. 
Each step should be specific and actionable. Number each step.

Task: $task

Plan:"""
        return llmProvider.complete(prompt)
    }
}
```

**Example Input/Output:**

```
Input: "I want to start a vegetable garden"
Output: "Step 1: Choose a sunny location with at least 6 hours of direct sunlight
Step 2: Test your soil pH and amend if needed
Step 3: Select vegetables suited to your climate zone
Step 4: Build raised beds or prepare in-ground rows
Step 5: Install irrigation or plan a watering schedule
Step 6: Plant seeds/seedlings according to spacing requirements
Step 7: Add mulch to retain moisture and suppress weeds
Step 8: Set up a fertilization schedule"
```

### 4B. ResearcherAgent

**Purpose:** Gathers information and provides research summaries. Often invokes WebSearchTool.

```kotlin
@Singleton
class ResearcherAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "ResearcherAgent"
    override val description = "Gathers information and provides research summaries"

    override suspend fun process(messages: List<Message>): String {
        val topic = messages.lastOrNull()?.content ?: ""
        val prompt = """You are a research specialist. Provide a comprehensive, well-organized summary about the following topic. 
Include key facts, different perspectives, and actionable insights.

Topic: $topic

Research Summary:"""
        return llmProvider.complete(prompt)
    }
}
```

### 4C. MemoryAgent

**Purpose:** Recalls and organizes memories about the user. Used when the user asks "what do you know about..." or "remember when...".

```kotlin
@Singleton
class MemoryAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "MemoryAgent"
    override val description = "Recalls and organizes memories about the user"

    override suspend fun process(messages: List<Message>): String {
        val query = messages.lastOrNull()?.content ?: ""
        val prompt = """You are a memory specialist. Based on what you know about the user from past conversations, 
summarize relevant memories and personal context about: $query

Be specific, reference actual details you've learned, and organize by category."""
        return llmProvider.complete(prompt)
    }
}
```

### 4D. CriticAgent

**Purpose:** Reviews and improves responses for quality, accuracy, and helpfulness. Used as the final step in complex orchestrations.

```kotlin
@Singleton
class CriticAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Agent {
    override val name = "CriticAgent"
    override val description = "Reviews and improves responses for quality"

    override suspend fun process(messages: List<Message>): String {
        val content = messages.lastOrNull()?.content ?: ""
        val prompt = """You are a quality critic. Review the following content and improve it for:
1. Accuracy and factual correctness
2. Clarity and readability
3. Completeness — are there gaps?
4. Tone — is it helpful and warm?

Original content:
$content

Improved version:"""
        return llmProvider.complete(prompt)
    }
}
```

### 4E. ExecutorAgent

**Purpose:** Executes skills and tools based on user requests. The only agent that interacts with SkillRegistry.

```kotlin
@Singleton
class ExecutorAgent @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val skillRegistry: SkillRegistry
) : Agent {
    override val name = "ExecutorAgent"
    override val description = "Executes skills and tools based on user requests"

    override suspend fun process(messages: List<Message>): String {
        val content = messages.lastOrNull()?.content ?: ""
        val matchedSkill = skillRegistry.matchSkill(content)
        return if (matchedSkill != null) {
            matchedSkill.execute(content)
        } else {
            llmProvider.complete(content)
        }
    }
}
```

---

## 5. AgentOrchestrator

### Complexity Levels

```kotlin
enum class TaskComplexity { Simple, Moderate, Complex }
```

| Complexity | Agents Used | Flow |
|-----------|------------|------|
| Simple | Executor only | Direct skill execution or LLM call |
| Moderate | Planner → Executor | Plan first, then execute |
| Complex | Researcher → Planner → Executor → Critic | Full pipeline |

### Implementation

```kotlin
@Singleton
class AgentOrchestrator @Inject constructor(
    private val plannerAgent: PlannerAgent,
    private val researcherAgent: ResearcherAgent,
    private val memoryAgent: MemoryAgent,
    private val criticAgent: CriticAgent,
    private val executorAgent: ExecutorAgent
) {
    suspend fun orchestrate(messages: List<Message>, complexity: TaskComplexity): String {
        return when (complexity) {
            TaskComplexity.Simple -> {
                executorAgent.process(messages)
            }
            TaskComplexity.Moderate -> {
                val plan = plannerAgent.process(messages)
                val execution = executorAgent.process(messages)
                "$plan\n\n$execution"
            }
            TaskComplexity.Complex -> {
                val research = researcherAgent.process(messages)
                val plan = plannerAgent.process(messages)
                val execution = executorAgent.process(messages)
                val review = criticAgent.process(
                    listOf(Message.assistant("$research\n$plan\n$execution"))
                )
                review
            }
        }
    }
}
```

### Complexity Determination (Future Enhancement)

In v1.0, complexity is mapped from `RouteType.Agent` and always uses `Complex`. Future enhancement:

```kotlin
// v1.1: LLM-based complexity assessment
suspend fun assessComplexity(message: String): TaskComplexity {
    val prompt = """Assess the complexity of this task:
"$message"

Respond with exactly one word: SIMPLE, MODERATE, or COMPLEX

SIMPLE: Single-step, direct answer
MODERATE: Multi-step, needs planning
COMPLEX: Requires research, planning, execution, and review"""
    
    val result = llmProvider.complete(prompt).trim().uppercase()
    return when {
        result.contains("SIMPLE") -> TaskComplexity.Simple
        result.contains("MODERATE") -> TaskComplexity.Moderate
        else -> TaskComplexity.Complex
    }
}
```

---

## 6. Routing Decision Tree — Complete

```
User sends message
   │
   ▼
RequestClassifier.classify(message)
   │
   ├── RouteType.Chat
   │      └──► MomoKernel → WrappedLlmProvider.streamChat()
   │             → Direct LLM stream, no agents, no skills
   │             → Memory extraction after response
   │
   ├── RouteType.Skill
   │      └──► ExecutorAgent.process()
   │             ├──► SkillRegistry.matchSkill(message)
   │             │      ├── Match found → skill.execute(message)
   │             │      └── No match → llmProvider.complete(message)
   │             └──► Result returned as response
   │
   ├── RouteType.Agent
   │      └──► AgentOrchestrator.orchestrate(messages, complexity)
   │             │
   │             ├── Simple → ExecutorAgent only
   │             ├── Moderate → PlannerAgent → ExecutorAgent
   │             └── Complex → ResearcherAgent → PlannerAgent
   │                            → ExecutorAgent → CriticAgent
   │
   └── RouteType.Interactive
          └──► LLM prompted with JSON schema instructions
                 → Returns ScreenDescriptor JSON
                 → InteractiveScreenParser.parse(json)
                 → InteractiveScreenRenderer renders Compose UI
```

---

## 7. Data Flow — Complex Agent Orchestration

```
User: "Research the best soil for tomatoes, plan a garden, and give me a critique"
   │
   ▼ RequestClassifier → RouteType.Agent
   │
   ▼ AgentOrchestrator.orchestrate(messages, Complex)
   │
   ├─► STEP 1: ResearcherAgent.process()
   │      prompt: "Provide research about: best soil for tomatoes"
   │      → LLM call (with memory-enriched system prompt)
   │      → "Tomatoes prefer loamy soil with pH 6.0-6.8..."
   │
   ├─► STEP 2: PlannerAgent.process()
   │      prompt: "Create a plan for: tomato garden"
   │      → LLM call
   │      → "Step 1: Test soil pH\nStep 2: Add compost..."
   │
   ├─► STEP 3: ExecutorAgent.process()
   │      prompt: original user message
   │      → SkillRegistry.matchSkill() → no skill match
   │      → llmProvider.complete()
   │      → "Based on the research and plan, here's your action list..."
   │
   └─► STEP 4: CriticAgent.process()
          input: concatenated research + plan + execution
          → LLM call
          → "Improved version with better accuracy and completeness..."
          │
          ▼ Final response delivered to user
```

---

## 8. Error Handling

| Scenario | Recovery |
|----------|----------|
| Agent LLM call fails | Return "I had trouble processing that. Could you try again?" |
| SkillRegistry returns no match | Fall back to `llmProvider.complete()` |
| AgentOrchestrator: one agent fails in Complex flow | Return partial results from completed agents |
| PlannerAgent returns empty plan | Skip plan, go straight to ExecutorAgent |
| CriticAgent fails | Return ExecutorAgent result without critique |

```kotlin
// Resilient orchestration
suspend fun orchestrate(messages: List<Message>, complexity: TaskComplexity): String {
    return try {
        when (complexity) {
            TaskComplexity.Simple -> executorAgent.process(messages)
            TaskComplexity.Moderate -> {
                val plan = plannerAgent.process(messages).ifEmpty { "" }
                val execution = executorAgent.process(messages)
                if (plan.isNotEmpty()) "$plan\n\n$execution" else execution
            }
            TaskComplexity.Complex -> {
                val research = try { researcherAgent.process(messages) } catch (_: Exception) { "" }
                val plan = try { plannerAgent.process(messages) } catch (_: Exception) { "" }
                val execution = try { executorAgent.process(messages) } catch (_: Exception) { "Unable to process." }
                val combined = "$research\n$plan\n$execution"
                try { criticAgent.process(listOf(Message.assistant(combined))) }
                catch (_: Exception) { combined }
            }
        }
    } catch (_: Exception) {
        "I had trouble processing that. Could you try again?"
    }
}
```

---

## 9. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `classify_chat` | "Hello, how are you?" | RouteType.Chat |
| `classify_skill` | "Write an article about AI" | RouteType.Skill |
| `classify_agent` | "Analyze the pros and cons of remote work" | RouteType.Agent |
| `classify_interactive` | "Show me a quiz about history" | RouteType.Interactive |
| `planner_stepByStep` | "Plan a birthday party" | Output contains numbered steps |
| `researcher_summary` | "Research electric cars" | Output contains organized research |
| `memoryAgent_recall` | "What do you know about my job?" | Output references stored memories |
| `critic_improves` | Feed mediocre text | Output is improved version |
| `executor_skillMatch` | "Search for AI news" | WebSearchSkill triggered |
| `executor_noSkillMatch` | "Tell me a joke" | Falls back to LLM complete |
| `orchestrator_simple` | Simple task | Only ExecutorAgent called |
| `orchestrator_moderate` | Moderate task | Planner + Executor called |
| `orchestrator_complex` | Complex task | All 4 agents called (Research→Plan→Execute→Critique) |
| `orchestrator_agentFailure` | ResearcherAgent throws | Partial results returned |
| `kernel_fullLoop` | Send "plan a trip" | Classify → Stream → Memory → Done |
