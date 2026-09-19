package com.example.trawa.data.repository

import com.example.trawa.data.network.ChatStreamEvent
import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface ChatRepository {
  val conversations: StateFlow<List<Conversation>>
  val activeConversationId: StateFlow<String?>
  val isTemporaryChat: StateFlow<Boolean>

  suspend fun fetchConversations(): Result<List<Conversation>>
  suspend fun selectConversation(id: String)
  suspend fun createNewConversation(title: String = "New Chat"): Conversation
  suspend fun toggleTemporaryChat(enabled: Boolean? = null): Boolean
  suspend fun getMessagesForConversation(conversationId: String): Result<List<ChatMessage>>
  fun streamAssistantReply(
    conversationId: String,
    userText: String,
    attachments: List<Attachment> = emptyList(),
    clientMessageId: String = UUID.randomUUID().toString()
  ): Flow<ChatStreamEvent>
  suspend fun deleteConversation(conversationId: String): Result<Unit>
}

class TrawaChatRepository(
  private val apiClient: TrawaApiClient
) : ChatRepository {

  private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
  override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

  private val _activeConversationId = MutableStateFlow<String?>(null)
  override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

  private val _isTemporaryChat = MutableStateFlow<Boolean>(false)
  override val isTemporaryChat: StateFlow<Boolean> = _isTemporaryChat.asStateFlow()

  override suspend fun fetchConversations(): Result<List<Conversation>> {
    val result = apiClient.getConversations()
    result.onSuccess { list ->
      _conversations.value = list
      if (_activeConversationId.value == null && list.isNotEmpty()) {
        _activeConversationId.value = list.first().id
      }
    }
    return result
  }

  override suspend fun selectConversation(id: String) {
    _activeConversationId.value = id
    val conv = _conversations.value.find { it.id == id }
    _isTemporaryChat.value = conv?.isTemporary == true
  }

  override suspend fun createNewConversation(title: String): Conversation {
    val isTemp = _isTemporaryChat.value
    val result = apiClient.createConversation(if (isTemp) "Temporary Chat" else title, isTemp)
    val conv = result.getOrElse { throw it }
    if (!isTemp) _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == conv.id }
    _activeConversationId.value = conv.id
    return conv
  }

  override suspend fun toggleTemporaryChat(enabled: Boolean?): Boolean {
    val next = enabled ?: !_isTemporaryChat.value
    _isTemporaryChat.value = next
    if (next) {
      val conv = apiClient.createConversation("Temporary Chat", true).getOrElse { throw it }
      _activeConversationId.value = conv.id
    } else {
      val first = _conversations.value.firstOrNull { !it.isTemporary }
      if (first != null) { _activeConversationId.value = first.id }
      else { val conv = apiClient.createConversation("New Chat", false).getOrElse { throw it }; _conversations.value = listOf(conv); _activeConversationId.value = conv.id }
    }
    return next
  }

  override suspend fun getMessagesForConversation(conversationId: String): Result<List<ChatMessage>> {
    if (conversationId.startsWith("temp-")) {
      return Result.success(emptyList())
    }
    return apiClient.getMessages(conversationId)
  }

  override fun streamAssistantReply(
    conversationId: String,
    userText: String,
    attachments: List<Attachment>,
    clientMessageId: String
  ): Flow<ChatStreamEvent> {
    return apiClient.sendChatMessageStream(
      conversationId = conversationId,
      message = userText,
      attachments = attachments,
      isTemporary = _isTemporaryChat.value,
      clientMessageId = clientMessageId
    )
  }

  override suspend fun deleteConversation(conversationId: String): Result<Unit> {
    val result = apiClient.deleteConversation(conversationId)
    _conversations.value = _conversations.value.filter { it.id != conversationId }
    if (_activeConversationId.value == conversationId) {
      _activeConversationId.value = _conversations.value.firstOrNull()?.id
    }
    return result
  }
}
