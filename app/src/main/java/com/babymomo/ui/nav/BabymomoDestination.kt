package com.babymomo.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class BabymomoDestination(val route: String, val label: String, val icon: ImageVector) {
    CHAT("chat", "Chat", Icons.Rounded.Chat),
    MEMORY("memory", "Memory", Icons.Rounded.Psychology),
    PROJECTS("projects", "Projects", Icons.Rounded.Folder),
    SKILLS("skills", "Skills", Icons.Rounded.AutoAwesome),
    AGENTS("agents", "Agents", Icons.Rounded.Hub),
    MODELS("models", "Models", Icons.Rounded.Download),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings);

    companion object {
        val START = CHAT
        val BOTTOM_BAR_ITEMS = listOf(CHAT, MEMORY, PROJECTS, MODELS)
        val DRAWER_ITEMS = listOf(CHAT, MEMORY, PROJECTS, SKILLS, AGENTS, MODELS, SETTINGS)
    }
}
