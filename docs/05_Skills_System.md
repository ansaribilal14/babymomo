# 05 — Skills System

## Module Overview

The Skills System provides higher-level, structured capabilities that go beyond simple tool execution. While tools are atomic operations (search, notify, run command), skills are multi-step workflows that may invoke the LLM multiple times, combine tool calls, and produce structured outputs. Skills are triggered by natural language via the `ExecutorAgent` keyword matching through `SkillRegistry`.

**Key Principle:** Skills are user-facing capabilities expressed in natural language. A user says "write an article about X" and the WriteArticleSkill handles the full workflow — planning, drafting, and revising.

---

## 1. Skill Interface

```kotlin
package com.babymomo.app.core.skills

interface Skill {
    /** Unique identifier for this skill. */
    val id: String

    /** Human-readable name shown in UI. */
    val name: String

    /** Brief description of what this skill does. */
    val description: String

    /** Keywords that trigger this skill. Case-insensitive matching. */
    val triggerKeywords: List<String>

    /** Execute the skill with the user's input. Returns the result string. */
    suspend fun execute(input: String): String
}
```

---

## 2. SkillRegistry

### Design

The `SkillRegistry` holds all registered skills and provides matching by keyword. It's injected as a `@Singleton` and uses Hilt constructor injection to receive all skill implementations.

```kotlin
@Singleton
class SkillRegistry @Inject constructor(
    private val writeArticleSkill: WriteArticleSkill,
    private val summarizeSkill: SummarizeSkill,
    private val webSearchSkill: WebSearchSkill,
    private val calendarSkill: CalendarSkill,
    private val shellSkill: ShellSkill,
    private val pdfSkill: PdfSkill
) {
    private val skills: List<Skill> by lazy {
        listOf(writeArticleSkill, summarizeSkill, webSearchSkill, calendarSkill, shellSkill, pdfSkill)
    }

    /** Find the first skill whose trigger keywords match the input text. */
    fun matchSkill(input: String): Skill? {
        val lower = input.lowercase()
        return skills.firstOrNull { skill ->
            skill.triggerKeywords.any { keyword -> lower.contains(keyword) }
        }
    }

    /** Get all registered skills (for UI display). */
    fun getAllSkills(): List<Skill> = skills

    /** Get a skill by its ID. */
    fun getSkillById(id: String): Skill? = skills.firstOrNull { it.id == id }
}
```

### Matching Algorithm

```
Input: "Write an article about machine learning"
   │
   ▼ Lowercase: "write an article about machine learning"
   │
   ▼ Check each skill's triggerKeywords:
   │  WriteArticleSkill: ["write", "article", "draft", "compose"] → "write" MATCH
   │
   ▼ Return WriteArticleSkill (first match wins)
```

### Priority Order

Skills are checked in registration order. If multiple skills could match, the first one wins:

1. WriteArticleSkill
2. SummarizeSkill
3. WebSearchSkill
4. CalendarSkill
5. ShellSkill
6. PdfSkill

---

## 3. All 6 Skills — Detailed Specs

### 3A. WriteArticleSkill

| Property | Value |
|----------|-------|
| id | `write_article` |
| name | Write Article |
| triggerKeywords | `["write", "article", "draft", "compose", "blog post", "essay"]` |

```kotlin
@Singleton
class WriteArticleSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val id = "write_article"
    override val name = "Write Article"
    override val description = "Drafts articles, blog posts, and essays on any topic"
    override val triggerKeywords = listOf("write", "article", "draft", "compose", "blog post", "essay")

    override suspend fun execute(input: String): String {
        // Step 1: Generate outline
        val outline = llmProvider.complete(
            "Create a detailed outline for an article about: $input. " +
            "Include main sections and key points for each section."
        )

        // Step 2: Draft full article
        val draft = llmProvider.complete(
            "Write a complete, well-structured article based on this outline:\n$outline\n\n" +
            "Topic: $input\nWrite the full article now:"
        )

        // Step 3: Review and polish
        val polished = llmProvider.complete(
            "Review and improve this article for clarity, flow, and engagement. " +
            "Fix any grammatical errors and improve transitions:\n\n$draft"
        )

        return polished
    }
}
```

**Execution Flow:**

```
"Write an article about AI in healthcare"
   │
   ├─► LLM: Generate outline → "1. Introduction\n2. Current Applications\n3. Challenges..."
   ├─► LLM: Draft article from outline → Full 500-1000 word article
   └─► LLM: Review and polish → Final improved version
```

### 3B. SummarizeSkill

| Property | Value |
|----------|-------|
| id | `summarize` |
| name | Summarize |
| triggerKeywords | `["summarize", "summary", "tldr", "brief", "condense", "key points"]` |

```kotlin
@Singleton
class SummarizeSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val id = "summarize"
    override val name = "Summarize"
    override val description = "Creates concise summaries of text or topics"
    override val triggerKeywords = listOf("summarize", "summary", "tldr", "brief", "condense", "key points")

    override suspend fun execute(input: String): String {
        return llmProvider.complete(
            "Provide a clear, concise summary with key bullet points for: $input\n\n" +
            "Format:\n**Summary:** [1-2 sentence overview]\n\n**Key Points:**\n- [point 1]\n- [point 2]\n- [point 3]"
        )
    }
}
```

### 3C. WebSearchSkill

| Property | Value |
|----------|-------|
| id | `web_search` |
| name | Web Search |
| triggerKeywords | `["search", "look up", "find information", "google", "lookup"]` |

```kotlin
@Singleton
class WebSearchSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val webSearchTool: WebSearchTool
) : Skill {
    override val id = "web_search"
    override val name = "Web Search"
    override val description = "Searches the web for current information"
    override val triggerKeywords = listOf("search", "look up", "find information", "google", "lookup")

    override suspend fun execute(input: String): String {
        // Step 1: Extract search query from natural language
        val searchQuery = llmProvider.complete(
            "Extract the most effective search query from this request. " +
            "Return ONLY the search query, nothing else: $input"
        )

        // Step 2: Execute web search
        val searchResults = webSearchTool.execute(
            buildJsonObject { put("query", searchQuery) }
        )

        // Step 3: Synthesize results
        return llmProvider.complete(
            "Based on these search results, provide a helpful answer to: $input\n\n" +
            "Search results:\n$searchResults"
        )
    }
}
```

### 3D. CalendarSkill

| Property | Value |
|----------|-------|
| id | `calendar` |
| name | Calendar |
| triggerKeywords | `["calendar", "schedule", "appointment", "meeting", "event"]` |

```kotlin
@Singleton
class CalendarSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val calendarReadTool: CalendarTool,
    private val calendarCreateTool: CalendarCreateTool
) : Skill {
    override val id = "calendar"
    override val name = "Calendar"
    override val description = "Manages calendar events and schedules"
    override val triggerKeywords = listOf("calendar", "schedule", "appointment", "meeting", "event")

    override suspend fun execute(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("create") || lower.contains("add") || lower.contains("schedule") -> {
                val eventDetails = llmProvider.complete(
                    "Extract event details from this request as JSON: {title, date, time}. $input"
                )
                calendarCreateTool.execute(
                    buildJsonObject {
                        put("title", eventDetails) // Simplified; real impl parses JSON
                    }
                )
            }
            else -> {
                val results = calendarReadTool.execute(
                    buildJsonObject { put("days_ahead", JsonPrimitive(7)) }
                )
                llmProvider.complete(
                    "Present these calendar events in a friendly, organized way:\n$results"
                )
            }
        }
    }
}
```

### 3E. ShellSkill

| Property | Value |
|----------|-------|
| id | `shell` |
| name | Shell |
| triggerKeywords | `["shell", "run", "execute", "command", "terminal", "bash"]` |

```kotlin
@Singleton
class ShellSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider,
    private val shellTool: ShellTool
) : Skill {
    override val id = "shell"
    override val name = "Shell"
    override val description = "Runs shell commands in the Linux sandbox"
    override val triggerKeywords = listOf("shell", "run", "execute", "command", "terminal", "bash")

    override suspend fun execute(input: String): String {
        // Step 1: Extract or generate the shell command
        val command = llmProvider.complete(
            "Convert this natural language request into a shell command. " +
            "Return ONLY the command, nothing else: $input"
        )

        // Step 2: Execute in sandbox
        val output = shellTool.execute(
            buildJsonObject { put("command", command) }
        )

        return "Command: `$command`\n\nOutput:\n$output"
    }
}
```

### 3F. PdfSkill (Stub)

| Property | Value |
|----------|-------|
| id | `pdf` |
| name | PDF |
| triggerKeywords | `["pdf", "document", "read pdf", "analyze pdf"]` |

```kotlin
@Singleton
class PdfSkill @Inject constructor() : Skill {
    override val id = "pdf"
    override val name = "PDF Analysis"
    override val description = "Analyzes PDF documents (coming in v1.1)"
    override val triggerKeywords = listOf("pdf", "document", "read pdf", "analyze pdf")

    override suspend fun execute(input: String): String {
        return "PDF analysis is not yet available. This feature will be released in v1.1. " +
               "For now, you can paste the text content of your document and I'll help analyze it."
    }
}
```

---

## 4. Trigger Keywords — Complete Reference

| Skill | Keywords |
|-------|----------|
| WriteArticle | write, article, draft, compose, blog post, essay |
| Summarize | summarize, summary, tldr, brief, condense, key points |
| WebSearch | search, look up, find information, google, lookup |
| Calendar | calendar, schedule, appointment, meeting, event |
| Shell | shell, run, execute, command, terminal, bash |
| PDF | pdf, document, read pdf, analyze pdf |

### Keyword Collision Resolution

When multiple skills share overlapping keywords (e.g., "search" appears in WebSearchSkill and could conflict with ShellSkill's "run"), the first registered skill wins. Registration order is the priority:

```
1. WriteArticleSkill  → "write" catches "write a script" before ShellSkill
2. SummarizeSkill     → no overlap
3. WebSearchSkill     → "search" catches "search for..." before ShellSkill
4. CalendarSkill      → "schedule" could overlap with ShellSkill, but CalendarSkill is first
5. ShellSkill         → "run", "execute", "bash"
6. PdfSkill           → "pdf", no overlap
```

---

## 5. Execution Flow

### Single-Skill Execution (via ExecutorAgent)

```
User: "Write a blog post about sustainable farming"
   │
   ▼ RequestClassifier.classify()
   │  → "write" keyword → RouteType.Skill
   │
   ▼ ExecutorAgent.process()
   │  → SkillRegistry.matchSkill("write a blog post about sustainable farming")
   │  → WriteArticleSkill matches on "write"
   │
   ▼ WriteArticleSkill.execute("write a blog post about sustainable farming")
   │  ├─► LLM: Generate outline
   │  ├─► LLM: Draft article from outline
   │  └─► LLM: Review and polish
   │
   ▼ Return final polished article
```

### Skill That Uses Tools (WebSearchSkill)

```
User: "Search for the latest news about AI regulation"
   │
   ▼ RequestClassifier → RouteType.Skill
   ▼ ExecutorAgent → SkillRegistry.matchSkill() → WebSearchSkill
   ▼ WebSearchSkill.execute()
   │  ├─► LLM: Extract search query → "AI regulation latest news 2026"
   │  ├─► WebSearchTool.execute({query: "AI regulation latest news 2026"})
   │  │      → "1. EU AI Act enforcement begins...\n2. US proposes new framework..."
   │  └─► LLM: Synthesize results → "Here's what I found about AI regulation..."
   │
   ▼ Return synthesized answer
```

---

## 6. Error Handling

| Scenario | Recovery |
|----------|----------|
| Skill LLM call fails (mid-workflow) | Return partial result with note about failure |
| WebSearchTool returns no results | LLM answers from its own knowledge |
| ShellTool: sandbox not installed | Return "Linux sandbox not ready. Enable it in Settings." |
| CalendarTool: permission denied | Return "Calendar permission not granted." |
| PdfSkill: stub, not implemented | Return status message about v1.1 availability |
| Multiple skills match keywords | First registered skill wins (no ambiguity) |
| No skill matches | ExecutorAgent falls back to `llmProvider.complete()` |

---

## 7. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `matchSkill_write` | "Write an article about cats" | WriteArticleSkill |
| `matchSkill_summarize` | "Summarize this text" | SummarizeSkill |
| `matchSkill_search` | "Search for AI news" | WebSearchSkill |
| `matchSkill_calendar` | "What's on my calendar?" | CalendarSkill |
| `matchSkill_shell` | "Run ls -la" | ShellSkill |
| `matchSkill_pdf` | "Read this PDF" | PdfSkill |
| `matchSkill_noMatch` | "Tell me a joke" | null → falls back to LLM |
| `writeArticle_fullFlow` | Execute WriteArticleSkill | 3 LLM calls, polished result |
| `summarize_returnsBullets` | Execute SummarizeSkill | Result contains bullet points |
| `webSearch_usesTool` | Execute WebSearchSkill | WebSearchTool.execute() called |
| `calendar_readsEvents` | "What's on my schedule?" | CalendarReadTool called |
| `calendar_createsEvent` | "Schedule a meeting tomorrow" | CalendarCreateTool called |
| `shell_executesCommand` | "Run echo hello" | ShellTool.execute() called |
| `pdf_stubReturns` | Execute PdfSkill | "not yet available" message |
| `skillRegistry_getAll` | getAllSkills() | 6 skills returned |
