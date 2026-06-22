package com.babymomo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.core.kernel.MomoKernel
import com.babymomo.data.db.dao.ConversationDao
import com.babymomo.data.db.entity.ConversationEntity
import com.babymomo.data.db.entity.MessageEntity
import com.babymomo.data.db.entity.MessageRole
import com.babymomo.data.db.entity.MessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val error: String? = null,
    val routingReason: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val kernel: MomoKernel,
    private val conversationDao: ConversationDao
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = conversationDao.activeConversationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startNewConversation() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val conv = ConversationEntity(id = "conv_" + UUID.randomUUID().toString().take(16), title = "New conversation", createdAt = now, updatedAt = now)
            conversationDao.upsertConversation(conv)
            _state.value = _state.value.copy(conversationId = conv.id, messages = emptyList(), error = null)
        }
    }

    fun openConversation(id: String) {
        viewModelScope.launch {
            val msgs = conversationDao.messages(id)
            _state.value = _state.value.copy(conversationId = id, messages = msgs, error = null)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val convId = _state.value.conversationId ?: run {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val conv = ConversationEntity(id = "conv_" + UUID.randomUUID().toString().take(16), title = text.take(40), createdAt = now, updatedAt = now)
                conversationDao.upsertConversation(conv)
                _state.value = _state.value.copy(conversationId = conv.id)
                sendMessage(text)
            }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val userMsg = MessageEntity(
                id = "msg_" + UUID.randomUUID().toString().take(16),
                conversationId = convId, role = MessageRole.USER, content = text,
                createdAt = now, status = MessageStatus.COMPLETE
            )
            conversationDao.upsertMessage(userMsg)
            _state.value = _state.value.copy(messages = _state.value.messages + userMsg, isStreaming = true, streamingText = "", error = null)

            val history = _state.value.messages.takeLast(10).map { m ->
                com.babymomo.core.llm.LlmMessage(com.babymomo.core.llm.LlmRole.valueOf(m.role.name), m.content)
            }

            val assistantMsgId = "msg_" + UUID.randomUUID().toString().take(16)
            val assistantStartedAt = System.currentTimeMillis()

            try {
                kernel.streamProcess(text, history).collect { event ->
                    when (event) {
                        is MomoKernel.KernelStreamEvent.Routing -> _state.value = _state.value.copy(routingReason = event.decision.reason)
                        is MomoKernel.KernelStreamEvent.Start -> _state.value = _state.value.copy(streamingText = "")
                        is MomoKernel.KernelStreamEvent.Token -> _state.value = _state.value.copy(streamingText = _state.value.streamingText + event.text)
                        is MomoKernel.KernelStreamEvent.Done -> {
                            val assistantMsg = MessageEntity(
                                id = assistantMsgId, conversationId = convId, role = MessageRole.ASSISTANT,
                                content = event.fullResponse, createdAt = System.currentTimeMillis(),
                                status = MessageStatus.COMPLETE, latencyMs = System.currentTimeMillis() - assistantStartedAt
                            )
                            conversationDao.upsertMessage(assistantMsg)
                            _state.value = _state.value.copy(messages = _state.value.messages + assistantMsg, isStreaming = false, streamingText = "", routingReason = null)
                            val conv = conversationDao.getConversation(convId)
                            if (conv != null && conv.title == "New conversation") {
                                conversationDao.updateConversation(conv.copy(title = text.take(40)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isStreaming = false, streamingText = "", error = e.message ?: "Failed to send message")
            }
        }
    }

    fun stopStreaming() { _state.value = _state.value.copy(isStreaming = false, streamingText = "") }
}
