package com.babymomo.app.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.babymomo.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, viewModel: ChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Text("Babymomo", color = ElectricTeal)
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MidnightBlack
            ),
            actions = {
                IconButton(onClick = { viewModel.createNewConversation() }) {
                    Icon(Icons.Filled.MoreVert, "Menu", tint = MutedBlue)
                }
            }
        )

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            // Streaming indicator
            if (uiState.isStreaming) {
                item {
                    StreamingIndicator()
                }
            }
        }

        // Routing reason chip
        uiState.routingReason?.let { reason ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = ElevatedNavy,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Routed via: $reason",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricTeal
                )
            }
        }

        // Input bar
        Surface(
            color = SurfaceNavy,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { /* Image attachment */ }) {
                    Icon(Icons.Filled.AttachFile, "Attach", tint = MutedBlue)
                }
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Talk to Babymomo...", color = DimBlue) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DividerBlue,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        cursorColor = ElectricTeal,
                        focusedContainerColor = ElevatedNavy,
                        unfocusedContainerColor = ElevatedNavy
                    ),
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = viewModel::sendMessage,
                    enabled = uiState.inputText.isNotBlank() && !uiState.isStreaming,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricTeal,
                        disabledContainerColor = DimBlue
                    )
                ) {
                    Text("→", color = MidnightBlack)
                }
            }
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) UserBubbleColor else AiBubbleColor,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Babymomo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MutedBlue else ElectricTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite
                )
                message.routingReason?.let { reason ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = ElevatedNavy,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = reason,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingIndicator() {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier.size(8.dp),
                color = ElectricTeal,
                shape = RoundedCornerShape(4.dp)
            ) {}
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text("Thinking...", style = MaterialTheme.typography.labelSmall, color = DimBlue)
    }
}
