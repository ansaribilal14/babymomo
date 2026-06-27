# 06 — Tools System

## Module Overview

The Tools System enables Babymomo's LLM to take real actions in the world — search the web, post notifications, read and create calendar events, run shell commands, and manage memories. Tools are registered in `ToolRegistry` and exposed to the LLM as function-calling definitions. When the LLM emits a `ToolCall` chunk during streaming, the kernel executes the tool and feeds the result back, continuing the conversation in a loop until the LLM produces a final text answer.

**Key Principle:** The tool loop is the Kai pattern — the LLM calls tools, gets results, and loops until it has a final answer. This is not a one-shot tool call; it's an iterative reasoning process.

---

## 1. Tool Interface

```kotlin
package com.babymomo.app.core.tools

interface Tool {
    /** Unique tool name used by the LLM in function calling. */
    val name: String

    /** Description that helps the LLM decide when to use this tool. */
    val description: String

    /** JSON Schema of the tool's input parameters. */
    val parameters: JsonObject

    /** Execute the tool with the given input and return the result as a string. */
    suspend fun execute(input: JsonObject): String
}
```

### JSON Schema Format

The `parameters` field follows the OpenAI function calling JSON Schema format:

```json
{
  "type": "object",
  "properties": {
    "query": {
      "type": "string",
      "description": "Search query"
    }
  }
}
```

---

## 2. ToolRegistry

### Design

`ToolRegistry` holds all tool implementations, provides them to the LLM as function definitions, and dispatches execution by name.

```kotlin
@Singleton
class ToolRegistry @Inject constructor(
    private val webSearch: WebSearchTool,
    private val notification: NotificationTool,
    private val calendarRead: CalendarTool,
    private val calendarCreate: CalendarCreateTool,
    private val shell: ShellTool,
    private val memoryStore: MemoryStoreTool,
    private val memoryRecall: MemoryRecallTool
) {
    private val tools: List<Tool> by lazy {
        listOf(webSearch, notification, calendarRead, calendarCreate, shell, memoryStore, memoryRecall)
    }

    /** Get all available tools for LLM function calling. */
    fun getAvailableTools(): List<Tool> = tools

    /** Execute a tool by name. Returns error string if tool not found or execution fails. */
    suspend fun execute(name: String, inputJson: String): String {
        val tool = tools.firstOrNull { it.name == name }
            ?: return "Unknown tool: $name"
        return try {
            val input = Json.parseToJsonElement(inputJson).jsonObject
            tool.execute(input)
        } catch (e: Exception) {
            "Tool error (${tool.name}): ${e.message}"
        }
    }
}
```

### MCP Tool Integration

MCP tools from connected servers are also registered dynamically. Each MCP tool is adapted into a `McpTool` that implements the `Tool` interface:

```kotlin
class McpTool(
    override val name: String,
    override val description: String,
    override val parameters: JsonObject,
    private val mcpClient: McpClient,
    private val serverUrl: String
) : Tool {
    override suspend fun execute(input: JsonObject): String {
        val args = input.mapValues { it.value.toString().trim('"') }
        return mcpClient.callTool(serverUrl, name, args)
    }
}
```

---

## 3. All 7 Tools — Detailed Specs

### 3A. WebSearchTool

| Property | Value |
|----------|-------|
| name | `web_search` |
| description | Search the web for current information |
| parameters | `{ "query": { "type": "string", "description": "Search query" } }` |

```kotlin
@Singleton
class WebSearchTool @Inject constructor() : Tool {
    override val name = "web_search"
    override val description = "Search the web for current information"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "Search query")
            })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val query = input["query"]?.toString()?.trim('"') ?: ""
        // v1.0: Placeholder — requires API configuration in Settings
        // v1.1: Real web search via configured API (SearXNG, Brave, etc.)
        return "Web search for '$query' - Configure web search API in Settings for real results."
    }
}
```

**v1.1 Enhancement:** Real web search integration via user-configured search API.

### 3B. NotificationTool

| Property | Value |
|----------|-------|
| name | `send_notification` |
| description | Post a local Android notification |
| parameters | `{ "title": string, "body": string }` |

```kotlin
@Singleton
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "send_notification"
    override val description = "Post a local Android notification"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("title", buildJsonObject { put("type", "string") })
            put("body", buildJsonObject { put("type", "string") })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val title = input["title"]?.toString()?.trim('"') ?: "Babymomo"
        val body = input["body"]?.toString()?.trim('"') ?: ""

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "babymomo_tools", "Babymomo Tools", NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(context, "babymomo_tools")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
        return "Notification sent: $title - $body"
    }
}
```

**Permissions Required:** `android.permission.POST_NOTIFICATIONS` (Android 13+)

### 3C. CalendarTool (Read)

| Property | Value |
|----------|-------|
| name | `calendar_read` |
| description | Read upcoming calendar events |
| parameters | `{ "days_ahead": integer }` |

```kotlin
@Singleton
class CalendarTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "calendar_read"
    override val description = "Read upcoming calendar events"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("days_ahead", buildJsonObject { put("type", "integer") })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        return try {
            val daysAhead = input["days_ahead"]?.toString()?.toIntOrNull() ?: 7
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DESCRIPTION
            )
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} > ?",
                arrayOf(System.currentTimeMillis().toString()),
                "${CalendarContract.Events.DTSTART} ASC LIMIT 10"
            )
            val events = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0)
                    val dtStart = it.getLong(1)
                    val date = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        .format(Date(dtStart))
                    events.add("- $title ($date)")
                }
            }
            if (events.isEmpty()) "No upcoming events in the next $daysAhead days."
            else "Upcoming events:\n${events.joinToString("\n")}"
        } catch (e: SecurityException) {
            "Calendar permission not granted. Enable in Settings."
        }
    }
}
```

**Permissions Required:** `android.permission.READ_CALENDAR`

### 3D. CalendarCreateTool

| Property | Value |
|----------|-------|
| name | `calendar_create` |
| description | Create a new calendar event |
| parameters | `{ "title": string, "start_time": string }` |

```kotlin
@Singleton
class CalendarCreateTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "calendar_create"
    override val description = "Create a new calendar event"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("title", buildJsonObject { put("type", "string") })
            put("start_time", buildJsonObject { put("type", "string") })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val title = input["title"]?.toString()?.trim('"') ?: return "Title is required"
        val startTime = input["start_time"]?.toString()?.trim('"') ?: return "Start time is required"

        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, parseTimeToMillis(startTime))
                put(CalendarContract.Events.DTEND, parseTimeToMillis(startTime) + 3600000) // 1 hour
                put(CalendarContract.Events.CALENDAR_ID, getPrimaryCalendarId())
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            "Calendar event created: $title"
        } catch (e: SecurityException) {
            "Calendar write permission not granted."
        }
    }

    private fun getPrimaryCalendarId(): Long {
        // Query for the primary calendar
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null, null
        )
        cursor?.use { if (it.moveToFirst()) return it.getLong(0) }
        return 1L // fallback
    }

    private fun parseTimeToMillis(timeStr: String): Long {
        // Parse ISO datetime or natural language time
        return try { Instant.parse(timeStr).toEpochMilli() }
        catch (_: Exception) { System.currentTimeMillis() + 3600000 } // default: 1 hour from now
    }
}
```

**Permissions Required:** `android.permission.WRITE_CALENDAR`

### 3E. ShellTool

| Property | Value |
|----------|-------|
| name | `shell_exec` |
| description | Run a shell command in the Linux sandbox |
| parameters | `{ "command": string }` |

```kotlin
@Singleton
class ShellTool @Inject constructor(
    private val linuxSandbox: LinuxSandbox
) : Tool {
    override val name = "shell_exec"
    override val description = "Run a shell command in the Linux sandbox"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("command", buildJsonObject {
                put("type", "string")
                put("description", "Shell command to execute")
            })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val command = input["command"]?.toString()?.trim('"') ?: ""
        return if (linuxSandbox.isReady()) {
            linuxSandbox.executeSuspend(command)
        } else {
            "Linux sandbox not ready. Enable it in Settings > Tools."
        }
    }
}
```

### 3F. MemoryStoreTool

| Property | Value |
|----------|-------|
| name | `memory_store` |
| description | Explicitly store a memory the AI decides is important |
| parameters | `{ "content": string, "type": string }` |

```kotlin
@Singleton
class MemoryStoreTool @Inject constructor(
    private val memoryService: MemoryService
) : Tool {
    override val name = "memory_store"
    override val description = "Explicitly store a memory the AI decides is important"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("content", buildJsonObject { put("type", "string") })
            put("type", buildJsonObject {
                put("type", "string")
                put("description", "WORKING, EPISODIC, SEMANTIC, or PROCEDURAL")
            })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val content = input["content"]?.toString()?.trim('"') ?: return "Content is required"
        val type = input["type"]?.toString()?.trim('"') ?: "SEMANTIC"
        memoryService.storeExplicitMemory(content, type)
        return "Memory stored: $content"
    }
}
```

### 3G. MemoryRecallTool

| Property | Value |
|----------|-------|
| name | `memory_recall` |
| description | Retrieve specific memories by keyword |
| parameters | `{ "query": string }` |

```kotlin
@Singleton
class MemoryRecallTool @Inject constructor(
    private val memoryService: MemoryService
) : Tool {
    override val name = "memory_recall"
    override val description = "Retrieve specific memories by keyword"
    override val parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject { put("type", "string") })
        })
    }

    override suspend fun execute(input: JsonObject): String {
        val query = input["query"]?.toString()?.trim('"') ?: return "Query is required"
        val memories = memoryService.recall(query, topK = 5)
        return if (memories.isEmpty()) {
            "No memories found for: $query"
        } else {
            memories.joinToString("\n") { "- ${it.content} [${it.type}]" }
        }
    }
}
```

---

## 4. Tool Loop Protocol

The tool loop is the core execution pattern. When the LLM emits a `ToolCall`, the kernel executes the tool, appends the result, and continues streaming.

### Protocol Diagram

```
User: "What's the weather in Tokyo and notify me if it's raining?"
   │
   ▼ MomoKernel.streamProcess()
   │
   ▼ LLM streams response...
   │  LlmChunk.Token("Let me check")
   │  LlmChunk.ToolCall(id="call_1", name="web_search", input={query: "Tokyo weather"})
   │
   ▼ ToolRegistry.execute("web_search", {query: "Tokyo weather"})
   │  → "Currently 18°C, cloudy with light rain in Tokyo"
   │
   ▼ Append ToolResult to message history
   │  messages += Message(role="tool", content="Currently 18°C, cloudy...", toolCallId="call_1")
   │
   ▼ Continue LLM stream...
   │  LlmChunk.Token("It's raining in Tokyo. Let me send you a notification.")
   │  LlmChunk.ToolCall(id="call_2", name="send_notification", input={title: "Weather Alert", body: "Rain in Tokyo"})
   │
   ▼ ToolRegistry.execute("send_notification", {...})
   │  → "Notification sent: Weather Alert - Rain in Tokyo"
   │
   ▼ Append ToolResult, continue stream
   │  LlmChunk.Token("Done! I've checked the weather and sent you a notification.")
   │  LlmChunk.Done
   │
   ▼ Final answer delivered to user
```

### Maximum Iteration Guard

```kotlin
// Prevent infinite tool loops
companion object {
    const val MAX_TOOL_ITERATIONS = 10
}

// In MomoKernel:
var toolIterations = 0
chunkFlow.collect { chunk ->
    when (chunk) {
        is LlmChunk.ToolCall -> {
            if (toolIterations >= MAX_TOOL_ITERATIONS) {
                emit(KernelOutput.Error("Maximum tool iterations reached"))
                return@collect
            }
            toolIterations++
            val result = toolRegistry.execute(chunk.name, chunk.input.toString())
            emit(KernelOutput.ToolUsed(chunk.name, result))
        }
        // ...
    }
}
```

---

## 5. Tool Definitions Sent to LLM

When the LLM is called, tools are formatted as OpenAI function definitions:

```json
[
  {
    "type": "function",
    "function": {
      "name": "web_search",
      "description": "Search the web for current information",
      "parameters": {
        "type": "object",
        "properties": {
          "query": { "type": "string", "description": "Search query" }
        }
      }
    }
  },
  {
    "type": "function",
    "function": {
      "name": "send_notification",
      "description": "Post a local Android notification",
      "parameters": {
        "type": "object",
        "properties": {
          "title": { "type": "string" },
          "body": { "type": "string" }
        }
      }
    }
  }
]
```

---

## 6. Error Handling

| Tool | Error | Recovery |
|------|-------|----------|
| WebSearchTool | No API configured | Return placeholder message |
| NotificationTool | Permission denied | Return "Notification permission not granted" |
| CalendarTool | Permission denied | Return "Calendar permission not granted" |
| CalendarCreateTool | Invalid time format | Default to 1 hour from now |
| ShellTool | Sandbox not ready | Return "Sandbox not ready" message |
| ShellTool | Command timeout (30s) | Kill process, return "Command timed out" |
| ShellTool | Command not found | Return stderr output |
| MemoryStoreTool | Empty content | Return "Content is required" |
| MemoryRecallTool | No memories found | Return "No memories found" |
| ToolRegistry | Unknown tool name | Return "Unknown tool: {name}" |
| ToolRegistry | Malformed JSON input | Return "Tool error: invalid input" |
| Any tool | Uncaught exception | Return "Tool error ({name}): {message}" |

---

## 7. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `registry_getAllTools` | getAvailableTools() | 7 tools returned |
| `registry_executeByName` | execute("web_search", ...) | Tool executes correctly |
| `registry_unknownTool` | execute("nonexistent", ...) | "Unknown tool: nonexistent" |
| `registry_malformedJson` | execute("web_search", "not json") | "Tool error: invalid input" |
| `webSearch_placeholderResponse` | Execute with query | Returns placeholder message |
| `notification_postsNotification` | Execute with title+body | Android notification posted |
| `notification_permissionDenied` | No POST_NOTIFICATIONS perm | Returns permission error |
| `calendarRead_returnsEvents` | Has calendar events | Returns formatted list |
| `calendarRead_noEvents` | No events in next 7 days | "No upcoming events" |
| `calendarRead_permissionDenied` | No READ_CALENDAR perm | Returns permission error |
| `calendarCreate_createsEvent` | Valid title + time | Event created in calendar |
| `shell_ready` | Sandbox installed | Command output returned |
| `shell_notReady` | Sandbox not installed | "Sandbox not ready" |
| `memoryStore_stores` | Store a memory | Memory persisted in DB |
| `memoryRecall_returnsMatches` | Recall with query | Matching memories returned |
| `memoryRecall_noMatches` | No matching memories | "No memories found" |
| `toolLoop_maxIterations` | LLM always returns ToolCall | Stops after 10 iterations |
| `toolLoop_multiTool` | Search + notify in one turn | Both tools execute |
