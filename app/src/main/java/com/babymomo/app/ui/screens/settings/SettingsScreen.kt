package com.babymomo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.babymomo.app.core.llm.RemoteLlmProvider
import com.babymomo.app.core.llm.WrappedLlmProvider
import com.babymomo.app.ui.nav.Routes
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val internetOff: Boolean = true,
    val sandboxEnabled: Boolean = false,
    val openaiKey: String = "",
    val openaiModel: String = "gpt-4o-mini",
    val nvidiaKey: String = "",
    val nvidiaModel: String = "meta/llama-3.1-8b-instruct",
    val openrouterKey: String = "",
    val openrouterModel: String = "openai/gpt-4o-mini",
    val soul: String = WrappedLlmProvider.DEFAULT_SOUL
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val remoteLlmProvider: RemoteLlmProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun saveProvider(name: String, apiKey: String, model: String) {
        remoteLlmProvider.saveProviderConfig(name, apiKey, model)
    }

    fun updateSoul(soul: String) { _uiState.update { it.copy(soul = soul) } }
    fun toggleInternet(off: Boolean) { _uiState.update { it.copy(internetOff = off) } }
    fun toggleSandbox(enabled: Boolean) { _uiState.update { it.copy(sandboxEnabled = enabled) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text("Settings", color = ElectricTeal) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack)
        )

        // AI Providers section
        SectionHeader("AI Providers")
        ProviderConfigCard("OpenAI", uiState.openaiKey, uiState.openaiModel, onSave = { key, model -> viewModel.saveProvider("openai", key, model) })
        ProviderConfigCard("NVIDIA NIM", uiState.nvidiaKey, uiState.nvidiaModel, onSave = { key, model -> viewModel.saveProvider("nvidia", key, model) })
        ProviderConfigCard("OpenRouter", uiState.openrouterKey, uiState.openrouterModel, onSave = { key, model -> viewModel.saveProvider("openrouter", key, model) })

        // Privacy section
        SectionHeader("Privacy")
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Internet off by default", color = PureWhite); Text("All data stays on device", color = DimBlue, style = MaterialTheme.typography.bodySmall) }
                Switch(checked = uiState.internetOff, onCheckedChange = viewModel::toggleInternet, colors = SwitchDefaults.colors(checkedTrackColor = ElectricTeal))
            }
        }

        // Soul section
        SectionHeader("Soul (System Prompt)")
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = uiState.soul,
                onValueChange = viewModel::updateSoul,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(150.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal, unfocusedContainerColor = ElevatedNavy)
            )
        }

        // Tools section
        SectionHeader("Tools")
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Linux Sandbox", color = PureWhite); Text("proot Alpine Linux", color = DimBlue, style = MaterialTheme.typography.bodySmall) }
                Switch(checked = uiState.sandboxEnabled, onCheckedChange = viewModel::toggleSandbox, colors = SwitchDefaults.colors(checkedTrackColor = ElectricTeal))
            }
        }

        TextButton(onClick = { navController.navigate(Routes.MCP) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("MCP Servers →", color = ElectricTeal) }
        TextButton(onClick = { navController.navigate(Routes.TERMINAL) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Terminal →", color = ElectricTeal) }
        TextButton(onClick = { navController.navigate(Routes.HEARTBEAT) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Heartbeat Log →", color = ElectricTeal) }
        TextButton(onClick = { navController.navigate(Routes.SKILLS) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Skills →", color = ElectricTeal) }

        // About
        SectionHeader("About")
        Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Babymomo v1.0.0", color = PureWhite)
                Text("One AI. One memory. One brain. Forever.", color = DimBlue, style = MaterialTheme.typography.bodySmall)
                Text("MIT License", color = DimBlue, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = ElectricTeal)
}

@Composable
fun ProviderConfigCard(name: String, initialKey: String, initialModel: String, onSave: (String, String) -> Unit) {
    var apiKey by remember { mutableStateOf(initialKey) }
    var model by remember { mutableStateOf(initialModel) }

    Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = PureWhite)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal, unfocusedContainerColor = ElevatedNavy))
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal, unfocusedContainerColor = ElevatedNavy))
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onSave(apiKey, model) }, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) { Text("Save") }
        }
    }
}
