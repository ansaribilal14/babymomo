package com.babymomo.app.ui.nav

import kotlinx.serialization.Serializable

sealed class Route {
    @Serializable
    data object Chat : Route()

    @Serializable
    data object Memory : Route()

    @Serializable
    data object Projects : Route()

    @Serializable
    data object Models : Route()

    @Serializable
    data object Settings : Route()

    @Serializable
    data object Skills : Route()

    @Serializable
    data object Heartbeat : Route()

    @Serializable
    data object Terminal : Route()

    @Serializable
    data object Mcp : Route()

    @Serializable
    data class Interactive(val descriptor: String) : Route()

    @Serializable
    data class ProjectDetail(val projectId: String) : Route()

    @Serializable
    data class MemoryDetail(val memoryId: String) : Route()

    @Serializable
    data class ChatConversation(val conversationId: String) : Route()
}
