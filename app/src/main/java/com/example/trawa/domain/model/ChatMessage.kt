package com.example.trawa.domain.model

enum class MessageRole {
  USER,
  ASSISTANT,
  SYSTEM,
  TOOL
}

enum class MessageStatus {
  SENDING,
  SENT,
  STREAMING,
  ERROR
}

data class ChatMessage(
  val id: String,
  val conversationId: String,
  val role: MessageRole,
  val content: String,
  val createdAt: Long = System.currentTimeMillis(),
  val status: MessageStatus = MessageStatus.SENT,
  val attachments: List<Attachment> = emptyList(),
  val clientMessageId: String? = null,
  val serverMessageId: String? = null,
  val errorMessage: String? = null
)
