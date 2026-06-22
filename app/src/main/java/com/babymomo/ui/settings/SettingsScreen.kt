package com.babymomo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("BABYMOMO")
        Text("v0.1.0 — Offline-first AI companion\nMemory graph + Multi-agent + Skills + Project System",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()
        SectionHeader("Privacy")
        SettingRow("Internet research", "Allow the Research agent to query the web. Off by default.", internetEnabled) { internetEnabled = it }

        HorizontalDivider()
        SectionHeader("Remote LLM (optional escape hatch)")
        SettingRow("Enable remote provider", "Off by default. When on, BABYMOMO will fall back to this provider when no local model is loaded. Your data goes to the provider you choose.", remoteEnabled) { remoteEnabled = it }
        if (remoteEnabled) {
            OutlinedTextField(value = remoteUrl, onValueChange = { remoteUrl = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = remoteKey, onValueChange = { remoteKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = remoteModel, onValueChange = { remoteModel = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Works with: OpenAI, Groq, OpenRouter, Ollama (10.0.2.2:11434 from emulator, LAN IP from a real phone)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()
        SectionHeader("Memory extraction")
        SettingRow("Auto-extract memories from chat", "After each turn, MOMO extracts entities, relations, and facts into the memory graph.", extractionEnabled) { extractionEnabled = it }

        HorizontalDivider()
        SectionHeader("Agents")
        SettingRow("Critic agent (high-stakes verification)", "When on, the Critic agent verifies plans and factual answers before they're returned to you.", criticEnabled) { criticEnabled = it }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
