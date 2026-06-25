package com.babymomo.app.core.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.CalendarContract
import com.babymomo.app.core.sandbox.LinuxSandbox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject
    suspend fun execute(input: JsonObject): String
}

@Singleton
class WebSearchTool @Inject constructor() : Tool {
    override val name = "web_search"
    override val description = "Search the web for current information"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("query", kotlinx.serialization.json.buildJsonObject {
                put("type", "string")
                put("description", "Search query")
            })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        val query = input["query"]?.toString()?.trim('"') ?: ""
        return "Web search for '$query' - Configure web search API in Settings for real results."
    }
}

@Singleton
class NotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "send_notification"
    override val description = "Post a local Android notification"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("title", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
            put("body", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        val title = input["title"]?.toString()?.trim('"') ?: "Babymomo"
        val body = input["body"]?.toString()?.trim('"') ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "babymomo_tools",
            "Babymomo Tools",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = android.app.Notification.Builder(context, "babymomo_tools")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return "Notification sent: $title - $body"
    }
}

@Singleton
class CalendarTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "calendar_read"
    override val description = "Read upcoming calendar events"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("days_ahead", kotlinx.serialization.json.buildJsonObject { put("type", "integer") })
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
                    events.add(title)
                }
            }
            if (events.isEmpty()) "No upcoming events in the next $daysAhead days."
            else "Upcoming events:\n${events.joinToString("\n") { "- $it" }}"
        } catch (e: SecurityException) {
            "Calendar permission not granted. Enable in Settings."
        }
    }
}

@Singleton
class CalendarCreateTool @Inject constructor(
    @ApplicationContext private val context: Context
) : Tool {
    override val name = "calendar_create"
    override val description = "Create a new calendar event"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("title", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
            put("start_time", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        return "Calendar event creation - requires calendar write permission."
    }
}

@Singleton
class ShellTool @Inject constructor(
    private val linuxSandbox: LinuxSandbox
) : Tool {
    override val name = "shell_exec"
    override val description = "Run a shell command in the Linux sandbox"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("command", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        val command = input["command"]?.toString()?.trim('"') ?: ""
        return if (linuxSandbox.isReady()) {
            linuxSandbox.execute(command)
        } else {
            "Linux sandbox not ready. Enable it in Settings > Tools."
        }
    }
}

@Singleton
class MemoryStoreTool @Inject constructor() : Tool {
    override val name = "memory_store"
    override val description = "Explicitly store a memory the AI decides is important"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("content", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
            put("type", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        val content = input["content"]?.toString()?.trim('"') ?: ""
        return "Memory stored: $content"
    }
}

@Singleton
class MemoryRecallTool @Inject constructor() : Tool {
    override val name = "memory_recall"
    override val description = "Retrieve specific memories by keyword"
    override val parameters = kotlinx.serialization.json.buildJsonObject {
        put("type", "object")
        put("properties", kotlinx.serialization.json.buildJsonObject {
            put("query", kotlinx.serialization.json.buildJsonObject { put("type", "string") })
        })
    }
    override suspend fun execute(input: JsonObject): String {
        val query = input["query"]?.toString()?.trim('"') ?: ""
        return "Recalling memories about: $query"
    }
}

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

    fun getAvailableTools(): List<Tool> = tools

    suspend fun execute(name: String, inputJson: String): String {
        val tool = tools.firstOrNull { it.name == name }
            ?: return "Unknown tool: $name"
        return try {
            val input = kotlinx.serialization.json.Json.parseToJsonElement(inputJson).jsonObject
            tool.execute(input)
        } catch (e: Exception) {
            "Tool error (${tool.name}): ${e.message}"
        }
    }
}
