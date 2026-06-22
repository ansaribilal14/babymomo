package com.babymomo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var remoteEnabled by remember { mutableStateOf(false) }
    var remoteUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var remoteKey by remember { mutableStateOf("") }
    var remoteModel by remember { mutableStateOf("gpt-4o-mini") }
    var internetEnabled by remember { mutableStateOf(false) }
    var extractionEnabled by remember { mutableStateOf(true) }
    var criticEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // About card
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "BABYMOMO",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "v0.1.0 — Offline-first AI companion\nMemory graph · Multi-agent · Skills · Project System",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        SectionCard(
            title = "Privacy",
            icon = Icons.Rounded.Lock
        ) {
            SettingRow(
                title = "Internet research",
                subtitle = "Allow the Research agent to query the web. Off by default.",
                checked = internetEnabled,
                onCheckedChange = { internetEnabled = it }
            )
        }

        SectionCard(
            title = "Remote LLM",
            icon = Icons.Rounded.Bolt,
            subtitle = "Optional escape hatch. When enabled, BABYMOMO falls back to this provider when no local model is loaded — your data goes to the provider you choose."
        ) {
            SettingRow(
                title = "Enable remote provider",
                subtitle = "Off by default. Falls back when no local model is loaded.",
                checked = remoteEnabled,
                onCheckedChange = { remoteEnabled = it }
            )
            if (remoteEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = remoteUrl, onValueChange = { remoteUrl = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = remoteKey, onValueChange = { remoteKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = remoteModel, onValueChange = { remoteModel = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Works with: OpenAI, Groq, OpenRouter, Ollama (10.0.2.2:11434 from emulator, LAN IP from a real phone)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(
            title = "Memory extraction",
            icon = Icons.Rounded.Memory
        ) {
            SettingRow(
                title = "Auto-extract memories from chat",
                subtitle = "After each turn, MOMO extracts entities, relations, and facts into the memory graph.",
                checked = extractionEnabled,
                onCheckedChange = { extractionEnabled = it }
            )
        }

        SectionCard(
            title = "Agents",
            icon = Icons.Rounded.Shield
        ) {
            SettingRow(
                title = "Critic agent (high-stakes verification)",
                subtitle = "When on, the Critic agent verifies plans and factual answers before they're returned to you.",
                checked = criticEnabled,
                onCheckedChange = { criticEnabled = it }
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
