package com.example.trawa.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trawa.data.network.ChatStreamEvent
import com.example.trawa.data.repository.AttachmentRepository
import com.example.trawa.data.repository.ChatRepository
import com.example.trawa.data.repository.VoiceService
import com.example.trawa.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
  private val chatRepository: ChatRepository,
  private val attachmentRepository: AttachmentRepository,
  val voiceService: VoiceService
) : ViewModel() {

  val conversations = chatRepository.conversations
  val activeConversationId = chatRepository.activeConversationId
  val isTemporaryChat = chatRepository.isTemporaryChat
  val stagedAttachments = attachmentRepository.stagedAttachments
  val voiceState = voiceService.voiceState
  val liveTranscript = voiceService.liveTranscript

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _composerText = MutableStateFlow("")
  val composerText: StateFlow<String> = _composerText.asStateFlow()

  private val _isStreaming = MutableStateFlow(false)
  val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _isCallSheetVisible = MutableStateFlow(false)
  val isCallSheetVisible: StateFlow<Boolean> = _isCallSheetVisible.asStateFlow()

  private var initialized = false

  /** Start network-backed chat state only after authentication succeeds. */
  fun initialize() {
    if (initialized) return
    initialized = true
    viewModelScope.launch {
      chatRepository.fetchConversations()
      if (activeConversationId.value == null) startNewChat()
      else loadMessages(activeConversationId.value!!)
    }

    viewModelScope.launch {
      activeConversationId.collect { convId ->
        if (initialized && convId != null) loadMessages(convId)
      }
    }
  }

  fun resetForAuthentication() {
    initialized = false
    voiceService.interrupt()
    _messages.value = emptyList()
    _composerText.value = ""
    _isStreaming.value = false
    _isCallSheetVisible.value = false
    attachmentRepository.clearStaged()
  }

  fun onComposerTextChanged(newText: String) {
    _composerText.value = newText
  }

  fun startNewChat() {
    viewModelScope.launch {
      voiceService.interrupt()
      try { chatRepository.createNewConversation() } catch (e: Exception) { _errorMessage.value = e.message ?: "Could not create a new chat." }
      _messages.value = emptyList()
      _composerText.value = ""
      attachmentRepository.clearStaged()
      _errorMessage.value = null
    }
  }

  fun selectConversation(id: String) {
    viewModelScope.launch {
      voiceService.interrupt()
      chatRepository.selectConversation(id)
      loadMessages(id)
    }
  }

  fun toggleTemporaryChat() {
    viewModelScope.launch {
      try {
        val enabled = chatRepository.toggleTemporaryChat()
        _messages.value = emptyList()
        _composerText.value = ""
        attachmentRepository.clearStaged()
        _errorMessage.value = null
      } catch (e: Exception) { _errorMessage.value = e.message ?: "Could not switch chat mode." }
    }
  }

  private fun loadMessages(conversationId: String) {
    viewModelScope.launch {
      val result = chatRepository.getMessagesForConversation(conversationId)
      result.onSuccess { list ->
        _messages.value = list
      }.onFailure {
        // If empty or fresh conversation, retain current list
      }
    }
  }

  fun sendMessage(overrideText: String? = null) {
    val textToSend = (overrideText ?: _composerText.value).trim()
    val attachmentsToSend = stagedAttachments.value

    if (textToSend.isBlank() && attachmentsToSend.isEmpty()) return
    if (_isStreaming.value) return

    val currentConvId = activeConversationId.value ?: run {
      _errorMessage.value = "Chat is still connecting to TRAWA. Please try again in a moment."
      return
    }

    val userMessageId = UUID.randomUUID().toString()
    val clientMessageId = UUID.randomUUID().toString()

    val userMessage = ChatMessage(
      id = userMessageId,
      conversationId = currentConvId,
      role = MessageRole.USER,
      content = textToSend,
      status = MessageStatus.SENT,
      attachments = attachmentsToSend,
      clientMessageId = clientMessageId
    )

    // Add user message to UI immediately
    _messages.value = _messages.value + userMessage
    _composerText.value = ""
    attachmentRepository.clearStaged()
    _errorMessage.value = null

    // Prepare assistant placeholder message
    val assistantMessageId = UUID.randomUUID().toString()
    val assistantPlaceholder = ChatMessage(
      id = assistantMessageId,
      conversationId = currentConvId,
      role = MessageRole.ASSISTANT,
      content = "",
      status = MessageStatus.STREAMING
    )
    _messages.value = _messages.value + assistantPlaceholder
    _isStreaming.value = true

    viewModelScope.launch {
      val uploadedAttachments = try {
        attachmentRepository.uploadAttachments(attachmentsToSend)
      } catch (e: Exception) {
        _isStreaming.value = false
        _errorMessage.value = e.message ?: "Attachment upload failed."
        _messages.value = _messages.value.filterNot { it.id == assistantMessageId }
        return@launch
      }
      val streamedBuilder = StringBuilder()
      chatRepository.streamAssistantReply(
        conversationId = currentConvId,
        userText = textToSend,
        attachments = uploadedAttachments,
        clientMessageId = clientMessageId
      ).collect { event ->
        when (event) {
          is ChatStreamEvent.MessageStart -> {
            // Keep placeholder streaming
          }
          is ChatStreamEvent.Delta -> {
            streamedBuilder.append(event.textChunk)
            val updatedText = streamedBuilder.toString()
            _messages.value = _messages.value.map { msg ->
              if (msg.id == assistantMessageId) {
                msg.copy(content = updatedText, status = MessageStatus.STREAMING)
              } else msg
            }
          }
          is ChatStreamEvent.MessageEnd -> {
            val finalContent = if (event.fullContent.isNotBlank()) event.fullContent else streamedBuilder.toString()
            _messages.value = _messages.value.map { msg ->
              if (msg.id == assistantMessageId) {
                msg.copy(content = finalContent, status = MessageStatus.SENT)
              } else msg
            }
            _isStreaming.value = false

            // Voice-call mode is a real turn-taking loop:
            // speech -> chat request -> AI text -> TTS -> listen again.
            if (_isCallSheetVisible.value && finalContent.isNotBlank()) {
              voiceService.speak(finalContent) {
                if (_isCallSheetVisible.value && !_isStreaming.value) {
                  voiceService.startListening { speech ->
                    if (speech.isNotBlank() && _isCallSheetVisible.value) {
                      sendMessage(speech)
                    }
                  }
                }
              }
            }
          }
          is ChatStreamEvent.StreamError -> {
            _messages.value = _messages.value.map { msg ->
              if (msg.id == assistantMessageId) {
                msg.copy(
                  content = if (streamedBuilder.isNotEmpty()) streamedBuilder.toString() else "Connection to 3AR V1 Pro could not be established.",
                  status = MessageStatus.ERROR,
                  errorMessage = event.errorMessage
                )
              } else msg
            }
            _isStreaming.value = false
            _errorMessage.value = event.errorMessage
          }
          else -> {}
        }
      }
    }
  }

  fun editMessage(targetUserMessage: ChatMessage, newText: String) {
    if (newText.isBlank()) return
    // Remove all messages following this user message and resend
    val index = _messages.value.indexOfFirst { it.id == targetUserMessage.id }
    if (index != -1) {
      _messages.value = _messages.value.take(index)
      sendMessage(newText)
    }
  }

  fun regenerateResponse(assistantMessage: ChatMessage) {
    if (_isStreaming.value) return
    val index = _messages.value.indexOfFirst { it.id == assistantMessage.id }
    if (index > 0) {
      val precedingUserMessage = _messages.value.take(index).lastOrNull { it.role == MessageRole.USER }
      if (precedingUserMessage != null) {
        _messages.value = _messages.value.take(index)
        sendMessage(precedingUserMessage.content)
      }
    }
  }

  fun readAloud(content: String) {
    if (voiceState.value == VoiceSessionState.SPEAKING) {
      voiceService.stopSpeaking()
    } else {
      voiceService.speak(content)
    }
  }

  fun addAttachmentUri(context: Context, uri: Uri, type: AttachmentType) {
    attachmentRepository.addFromUri(context, uri, type)
  }

  fun removeAttachment(id: String) {
    attachmentRepository.removeAttachment(id)
  }

  fun startSpeechInput() {
    voiceService.startListening { transcript ->
      if (transcript.isNotBlank()) {
        _composerText.value = if (_composerText.value.isBlank()) transcript else "${_composerText.value} $transcript"
      }
    }
  }

  fun stopSpeechInput() {
    voiceService.stopListening()
  }

  fun openCallSheet() {
    if (_isCallSheetVisible.value) return
    _isCallSheetVisible.value = true
    voiceService.startListening { speech ->
      if (speech.isNotBlank() && _isCallSheetVisible.value) {
        sendMessage(speech)
      }
    }
  }

  fun closeCallSheet() {
    _isCallSheetVisible.value = false
    voiceService.interrupt()
  }

  fun clearError() {
    _errorMessage.value = null
  }
}
