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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Local mutable copies for the remote fields — committed on each change
    var remoteEnabled by remember(settings.remoteEnabled) { mutableStateOf(settings.remoteEnabled) }
    var remoteUrl by remember(settings.remoteBaseUrl) { mutableStateOf(settings.remoteBaseUrl) }
    var remoteKey by remember(settings.remoteApiKey) { mutableStateOf(settings.remoteApiKey) }
    var remoteModel by remember(settings.remoteModel) { mutableStateOf(settings.remoteModel) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // About card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "BABYMOMO",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "v0.4.0 — Real AI companion\nMemory graph · Multi-agent · Skills · Projects",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // REMOTE LLM — the critical section. Pre-configured for Groq (free + fast).
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("AI Brain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect a free Groq API key to get REAL AI responses instantly. " +
                    "Groq is free, blazing fast, and runs Llama 3.3 70B. " +
                    "Your conversations stay between you and Groq.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // Enable toggle
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable AI brain", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (remoteEnabled) "Connected — real AI responses are live" else "Off — using mock brain (canned responses)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remoteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = remoteEnabled,
                        onCheckedChange = {
                            remoteEnabled = it
                            vm.updateRemoteConfig(remoteEnabled, remoteUrl, remoteKey, remoteModel)
                        }
                    )
                }

                if (remoteEnabled) {
                    Spacer(Modifier.height(16.dp))
                    // API key field — the most important input
                    OutlinedTextField(
                        value = remoteKey,
                        onValueChange = {
                            remoteKey = it
                            vm.updateRemoteConfig(remoteEnabled, remoteUrl, remoteKey, remoteModel)
                        },
                        label = { Text("Groq API Key") },
                        placeholder = { Text("gsk_...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Get a FREE key at console.groq.com → API Keys",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )

                    Spacer(Modifier.height(16.dp))
                    // Advanced settings (collapsible in a real app — keeping flat for v0.4)
                    Text("Advanced", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remoteUrl,
                        onValueChange = {
                            remoteUrl = it
                            vm.updateRemoteConfig(remoteEnabled, remoteUrl, remoteKey, remoteModel)
                        },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = remoteModel,
                        onValueChange = {
                            remoteModel = it
                            vm.updateRemoteConfig(remoteEnabled, remoteUrl, remoteKey, remoteModel)
                        },
                        label = { Text("Model name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Also works with: OpenAI (api.openai.com/v1), OpenRouter, Ollama (10.0.2.2:11434 from emulator). " +
                        "Popular Groq models: llama-3.3-70b-versatile, llama-3.1-8b-instant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // HuggingFace token — for downloading gated models (Gemma, Llama, etc.)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Model Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Some models (Gemma, Llama) require accepting their license on HuggingFace. " +
                    "Paste a free HuggingFace token here to download them. " +
                    "Get one at huggingface.co/settings/tokens (Read access is enough).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.hfToken,
                    onValueChange = { vm.setHfToken(it) },
                    label = { Text("HuggingFace Token") },
                    placeholder = { Text("hf_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                if (settings.hfToken.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "✓ Token saved — gated models (Gemma, Llama) will download successfully",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Privacy
        SectionCard(title = "Privacy", icon = Icons.Rounded.Lock) {
            SettingRow(
                title = "Internet research",
                subtitle = "Allow the Research agent to query the web. Off by default.",
                checked = settings.internetEnabled,
                onCheckedChange = { vm.setInternetEnabled(it) }
            )
        }

        // Memory
        SectionCard(title = "Memory", icon = Icons.Rounded.Memory) {
            SettingRow(
                title = "Auto-extract memories from chat",
                subtitle = "After each turn, MOMO extracts entities, relations, and facts into the memory graph.",
                checked = settings.extractionEnabled,
                onCheckedChange = { vm.setExtractionEnabled(it) }
            )
        }

        // Agents
        SectionCard(title = "Agents", icon = Icons.Rounded.Shield) {
            SettingRow(
                title = "Critic agent",
                subtitle = "Verifies plans and factual answers before returning them.",
                checked = settings.criticEnabled,
                onCheckedChange = { vm.setCriticEnabled(it) }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
