package com.example.trawa.data.network

import com.example.trawa.domain.model.GeneratedArtifact
import com.example.trawa.domain.model.MessageRole

sealed interface ChatStreamEvent {
  data class MessageStart(val messageId: String, val role: MessageRole) : ChatStreamEvent
  data class Delta(val textChunk: String) : ChatStreamEvent
  data class ToolCall(val toolName: String, val details: String) : ChatStreamEvent
  data class ArtifactEvent(val artifact: GeneratedArtifact) : ChatStreamEvent
  data class MessageEnd(val fullContent: String, val serverMessageId: String? = null) : ChatStreamEvent
  data class StreamError(val errorMessage: String, val code: Int? = null) : ChatStreamEvent
}
