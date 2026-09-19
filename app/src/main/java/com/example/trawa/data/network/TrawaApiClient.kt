package com.example.trawa.data.network

import com.example.trawa.domain.model.Attachment
import com.example.trawa.domain.model.ChatMessage
import com.example.trawa.domain.model.Conversation
import com.example.trawa.domain.model.MemoryItem
import com.example.trawa.domain.model.NetworkConfig
import com.example.trawa.domain.model.PersonalizationSettings
import com.example.trawa.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * TRAWA API Client Interface.
 *
 * All Android UI and ViewModel components interact solely with this abstraction
 * or through Repositories. The Android app has NO knowledge of individual AI providers
 * (Gemini, Groq, Mistral, OpenRouter, NVIDIA, etc.) which remain behind the TRAWA
 * 3AR V1 Pro backend router.
 */
interface TrawaApiClient {
  fun getBaseUrl(): String
  fun setBaseUrl(newUrl: String)
  fun isProductionReady(): Boolean

  // Authentication
  suspend fun login(email: String, password: String): Result<Session>
  suspend fun signUp(email: String, password: String, name: String): Result<Session?>
  suspend fun sendOtp(email: String): Result<Unit>
  suspend fun verifyOtp(email: String, code: String, name: String): Result<Session>
  suspend fun logout(): Result<Unit>
  suspend fun getCurrentSession(): Session?
  suspend fun resetPassword(email: String): Result<Unit>

  // Conversations
  suspend fun getConversations(): Result<List<Conversation>>
  suspend fun createConversation(title: String, isTemporary: Boolean = false): Result<Conversation>
  suspend fun deleteConversation(conversationId: String): Result<Unit>
  suspend fun getMessages(conversationId: String): Result<List<ChatMessage>>

  // Streaming Chat with 3AR V1 Pro
  fun sendChatMessageStream(
    conversationId: String,
    message: String,
    attachments: List<Attachment> = emptyList(),
    isTemporary: Boolean = false,
    clientMessageId: String
  ): Flow<ChatStreamEvent>

  // Attachments
  suspend fun uploadAttachment(attachment: Attachment): Result<Attachment>

  // Profile & Memory
  suspend fun getPersonalization(): Result<PersonalizationSettings>
  suspend fun updatePersonalization(settings: PersonalizationSettings): Result<Unit>
  suspend fun getMemories(): Result<List<MemoryItem>>
  suspend fun addMemory(content: String): Result<MemoryItem>
  suspend fun deleteMemory(memoryId: String): Result<Unit>
}
