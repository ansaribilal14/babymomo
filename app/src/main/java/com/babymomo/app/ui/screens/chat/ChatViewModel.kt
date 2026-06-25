package com.babymomo.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.app.core.kernel.KernelOutput
import com.babymomo.app.core.kernel.MomoKernel
import com.babymomo.app.core.llm.model.Message
import com.babymomo.app.data.db.dao.ConversationDao
import com.babymomo.app.data.db.dao.MessageDao
import com.babymomo.app.data.db.entities.ConversationEntity
import com.babymomo.app.data.db.entities.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val routingReason: String? = null,
    val currentConversationId: String? = null
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val routingReason: String? = null,
    val imageUri: String? = null,
    val isStreaming: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val momoKernel: MomoKernel,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentMessages = mutableListOf<Message>()

    init {
        createNewConversation()
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        _uiState.update { it.copy(inputText = "", isStreaming = true) }

        val userMessage = Message.user(text)
        currentMessages.add(userMessage)

        // Add user message to UI
        val userChatMsg = ChatMessage(
            id = "msg_${System.currentTimeMillis()}_user",
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(messages = it.messages + userChatMsg) }

        // Save to DB
        saveMessage(userChatMsg)

        // Process through MomoKernel
        viewModelScope.launch {
            val assistantMsgId = "msg_${System.currentTimeMillis()}_asst"
            val assistantBuilder = StringBuilder()
            var routingReason: String? = null

            _uiState.update {
                it.copy(messages = it.messages + ChatMessage(
                    id = assistantMsgId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true
                ))
            }

            momoKernel.streamProcess(currentMessages.toList()).collect { output ->
                when (output) {
                    is KernelOutput.Token -> {
                        assistantBuilder.append(output.text)
                        val currentText = assistantBuilder.toString()
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(content = currentText)
                                else msg
                            })
                        }
                    }
                    is KernelOutput.Done -> {
                        routingReason = output.routingReason
                        val finalText = assistantBuilder.toString()
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(
                                        content = finalText,
                                        isStreaming = false,
                                        routingReason = routingReason
                                    )
                                    else msg
                                },
                                isStreaming = false,
                                routingReason = routingReason
                            )
                        }
                        // Save assistant message
                        saveMessage(ChatMessage(
                            id = assistantMsgId,
                            role = "assistant",
                            content = finalText,
                            timestamp = System.currentTimeMillis(),
                            routingReason = routingReason
                        ))
                        currentMessages.add(Message.assistant(finalText))
                    }
                    is KernelOutput.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(
                                        content = "Error: ${output.message}",
                                        isStreaming = false
                                    )
                                    else msg
                                },
                                isStreaming = false
                            )
                        }
                    }
                    is KernelOutput.ToolUsed -> {
                        // Tool usage notification handled in UI
                    }
                }
            }
        }
    }

    fun createNewConversation() {
        val convId = "conv_${System.currentTimeMillis()}"
        viewModelScope.launch {
            conversationDao.insert(
                ConversationEntity(
                    id = convId,
                    title = "New conversation",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update {
                it.copy(
                    currentConversationId = convId,
                    messages = emptyList(),
                    routingReason = null
                )
            }
            currentMessages.clear()
        }
    }

    private fun saveMessage(msg: ChatMessage) {
        viewModelScope.launch {
            val convId = _uiState.value.currentConversationId ?: return@launch
            messageDao.insert(
                MessageEntity(
                    id = msg.id,
                    conversationId = convId,
                    role = msg.role,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    routingReason = msg.routingReason,
                    imageUri = msg.imageUri
                )
            )
            // Update conversation timestamp
            val conv = conversationDao.getById(convId)
            if (conv != null) {
                conversationDao.update(conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    title = if (conv.title == "New conversation" && msg.role == "user") {
                        msg.content.take(50)
                    } else conv.title
                ))
            }
        }
    }
}
