package com.babymomo.app.ui.screens.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.babymomo.app.core.sandbox.LinuxSandbox
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class TerminalUiState(
    val output: List<String> = emptyList(),
    val inputText: String = "",
    val sandboxReady: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val linuxSandbox: LinuxSandbox
) : ViewModel() {
    private val _uiState = MutableStateFlow(TerminalUiState(sandboxReady = linuxSandbox.isReady()))
    val uiState: StateFlow<TerminalUiState> = _uiState

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun executeCommand() {
        val cmd = _uiState.value.inputText.trim()
        if (cmd.isEmpty()) return
        _uiState.update { it.copy(inputText = "") }

        val output = mutableListOf<String>()
        output.addAll(_uiState.value.output)
        output.add("\$ $cmd")

        val result = linuxSandbox.execute(cmd)
        output.add(result)

        _uiState.update { it.copy(output = output) }
    }

    fun installSandbox() {
        kotlinx.coroutines.MainScope().launch {
            linuxSandbox.install()
            _uiState.update { it.copy(sandboxReady = true, output = listOf("Alpine Linux sandbox installed successfully.")) }
        }
    }

    fun clear() = _uiState.update { it.copy(output = emptyList()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController, viewModel: TerminalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Terminal", color = ElectricTeal) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack),
            actions = {
                TextButton(onClick = viewModel::clear) { Text("Clear", color = DimBlue) }
            }
        )

        if (!uiState.sandboxReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Linux sandbox not installed", color = DimBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::installSandbox, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                        Text("Install Alpine Linux (~3MB)")
                    }
                }
            }
        } else {
            // Terminal output
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.output) { line ->
                    SelectionContainer {
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line.startsWith("$")) ElectricTeal else PureWhite
                        )
                    }
                }
            }

            // Input bar
            Surface(color = SurfaceNavy) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$ ", color = ElectricTeal, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Enter command...", color = DimBlue) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal, unfocusedContainerColor = ElevatedNavy),
                        singleLine = true
                    )
                    FilledIconButton(onClick = viewModel::executeCommand, colors = IconButtonDefaults.filledIconButtonColors(containerColor = ElectricTeal)) {
                        Text("↵", color = MidnightBlack)
                    }
                }
            }
        }
    }
}
