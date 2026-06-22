package com.babymomo.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class AgentInfo(val id: String, val name: String, val desc: String, val status: String)

@Composable
fun AgentsScreen() {
    val agents = listOf(
        AgentInfo("planner", "Planner", "Creates plans, roadmaps, breaks tasks", "Idle"),
        AgentInfo("researcher", "Researcher", "Collects info, analyzes, summarizes", "Idle"),
        AgentInfo("memory", "Memory", "Stores + retrieves + connects memories", "Ready"),
        AgentInfo("critic", "Critic", "Checks mistakes, verifies logic", "Idle"),
        AgentInfo("executor", "Executor", "Performs actions, runs skills", "Ready")
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(agents) { a -> AgentCard(a) }
    }
}

@Composable
private fun AgentCard(a: AgentInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(a.name.first().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(a.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = {}, label = { Text(a.status, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
