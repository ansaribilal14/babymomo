package com.babymomo.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.R
import com.babymomo.ui.common.MomoAvatar
import com.babymomo.data.db.entity.MessageEntity
import com.babymomo.data.db.entity.MessageRole
import com.babymomo.ui.theme.BubbleMomoShape
import com.babymomo.ui.theme.BubbleUserShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AGENT_KEYWORDS = listOf(
    Triple("planner", "Planner", Icons.Rounded.EventNote),
    Triple("research", "Researcher", Icons.Rounded.AutoAwesome),
    Triple("memory", "Memory", Icons.Rounded.Psychology),
    Triple("critic", "Critic", Icons.Rounded.Psychology),
    Triple("executor", "Executor", Icons.Rounded.Folder),
)

@Composable
fun ChatScreen(vm: ChatViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Track whether the user has scrolled away from the bottom. We only auto-scroll when they
    // are still near the bottom of the conversation (standard chat UX).
    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || last >= total - 2
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText) {
        if (isAtBottom && (state.messages.isNotEmpty() || state.streamingText.isNotEmpty())) {
            // Defer a frame so the newly-inserted item is measured first.
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(state.messages.size.coerceAtLeast(0) + if (state.streamingText.isNotEmpty()) 1 else 0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.routingReason != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            RoutingChipRow(state.routingReason)
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
                EmptyChatState(onPromptClicked = { vm.sendMessage(it); keyboard?.hide() })
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                    if (state.streamingText.isNotEmpty()) {
                        item { StreamingBubble(state.streamingText) }
                    } else if (state.isStreaming) {
                        item { TypingBubble() }
                    }
                }
            }
        }

        AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
            if (state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        ChatInputBar(
            isStreaming = state.isStreaming,
            onSend = { text -> vm.sendMessage(text); keyboard?.hide() },
            onStop = { vm.stopStreaming() }
        )
    }
}

@Composable
private fun RoutingChipRow(reason: String?) {
    if (reason == null) return
    val lower = reason.lowercase(Locale.ROOT)
    val activeAgents = AGENT_KEYWORDS.filter { (kw, _, _) -> kw in lower }
    val display = if (activeAgents.isEmpty()) listOf(AGENT_KEYWORDS.first { it.first == "planner" }) else activeAgents
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Routing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            display.forEach { (_, label, icon) ->
                AgentMiniChip(label = label, icon = icon)
            }
            if (reason.isNotBlank()) {
                Spacer(Modifier.weight(1f))
                Text(
                    reason.take(48),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AgentMiniChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyChatState(onPromptClicked: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MomoAvatar(size = 96.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.empty_chat_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_chat_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )
            Spacer(Modifier.height(28.dp))
            PromptSuggestions(onPromptClicked)
        }
    }
}

@Composable
private fun PromptSuggestions(onPromptClicked: (String) -> Unit) {
    val prompts = remember {
        listOf(
            "Tell me about your day" to Icons.Rounded.AutoAwesome,
            "Plan a project" to Icons.Rounded.EventNote,
            "What do you remember about me?" to Icons.Rounded.Psychology
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        prompts.forEach { (text, icon) ->
            SuggestionChip(text = text, icon = icon) { onPromptClicked(text) }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageEntity) {
    val isUser = msg.role == MessageRole.USER
    val time = remember(msg.createdAt) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.createdAt))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = if (isUser) BubbleUserShape else BubbleMomoShape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isUser && msg.modelId != null) {
                Text(
                    msg.modelId.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = BubbleMomoShape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BlinkingCursor()
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "typing-phase"
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = BubbleMomoShape,
            modifier = Modifier.widthIn(max = 80.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val scale = when {
                        phase > i && phase < i + 1 -> 1f - (phase - i) * 0.6f
                        phase > i + 1 && phase < i + 2 -> 0.4f + (phase - i - 1) * 0.6f
                        else -> 0.6f
                    }
                    val alpha = (0.4f + (1f - scale) * 0.6f).coerceIn(0.4f, 1f)
                    Dot(size = 6.dp * scale + 2.dp, alpha = alpha)
                }
            }
        }
    }
}

@Composable
private fun Dot(size: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor-alpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp, bottom = 2.dp)
            .size(width = 2.dp, height = 16.dp)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}

@Composable
private fun ChatInputBar(isStreaming: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.placeholder_chat_input),
                        fontStyle = FontStyle.Italic
                    )
                },
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions.Default,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (isStreaming) {
                        FilledTonalIconButton(
                            onClick = onStop,
                            modifier = Modifier.padding(end = 4.dp, bottom = 2.dp).size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.action_stop))
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (canSend) {
                                    onSend(text.trim())
                                    text = ""
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier.padding(end = 4.dp, bottom = 2.dp).size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = stringResource(R.string.action_send))
                        }
                    }
                }
            )
        }
    }
}
