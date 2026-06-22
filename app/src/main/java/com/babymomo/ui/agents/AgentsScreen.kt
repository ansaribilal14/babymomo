package com.babymomo.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class AgentInfo(
    val id: String,
    val name: String,
    val desc: String,
    val status: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AgentsScreen() {
    val agents = listOf(
        AgentInfo("planner", "Planner", "Creates plans, roadmaps, breaks tasks", "Idle", Icons.Rounded.Psychology, Color(0xFFC2691F)),
        AgentInfo("researcher", "Researcher", "Collects info, analyzes, summarizes", "Idle", Icons.Rounded.AutoAwesome, Color(0xFF3F6485)),
        AgentInfo("memory", "Memory", "Stores + retrieves + connects memories", "Ready", Icons.Rounded.Folder, Color(0xFF5B7A4F)),
        AgentInfo("critic", "Critic", "Checks mistakes, verifies logic", "Idle", Icons.Rounded.Shield, Color(0xFFB0492E)),
        AgentInfo("executor", "Executor", "Performs actions, runs skills", "Ready", Icons.Rounded.Bolt, Color(0xFF7A4FC2))
    )
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(agents) { a -> AgentCard(a) }
    }
}

@Composable
private fun AgentCard(a: AgentInfo) {
    val isReady = a.status.equals("Ready", ignoreCase = true)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar = colored circle with the agent's icon
            Surface(
                color = a.color.copy(alpha = 0.16f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(a.icon, contentDescription = null, tint = a.color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusDot(isReady)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        a.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    a.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusDot(ready: Boolean) {
    val color = if (ready) Color(0xFF4FAE6B) else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color, CircleShape)
    )
}
