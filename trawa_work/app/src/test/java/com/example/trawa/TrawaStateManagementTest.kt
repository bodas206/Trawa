package com.example.trawa

import com.example.trawa.data.network.ChatStreamEvent
import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.data.repository.TrawaAttachmentRepository
import com.example.trawa.data.repository.TrawaAuthRepository
import com.example.trawa.data.repository.TrawaChatRepository
import com.example.trawa.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrawaStateManagementTest {

  // Mock API client for pure unit testing
  private val fakeApiClient = object : TrawaApiClient {
    override fun getBaseUrl(): String = "https://api.trawa.ai"
    override fun setBaseUrl(newUrl: String) {}
    override fun isProductionReady(): Boolean = true

    override suspend fun login(email: String, password: String): Result<Session> {
      return if (email == "test@trawa.ai" && password == "password123") {
        Result.success(
          Session(
            token = "jwt_token_abc",
            user = User(id = "u1", email = email, displayName = "Test User"),
            expiresAt = System.currentTimeMillis() + 3600000
          )
        )
      } else {
        Result.failure(Exception("Invalid credentials"))
      }
    }

    override suspend fun logout(): Result<Unit> = Result.success(Unit)
    override suspend fun getCurrentSession(): Session? = null
    override suspend fun resetPassword(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(emptyList())
    override suspend fun createConversation(title: String, isTemporary: Boolean): Result<Conversation> =
      Result.success(Conversation(id = UUID.randomUUID().toString(), title = title, isTemporary = isTemporary))
    override suspend fun deleteConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getMessages(conversationId: String): Result<List<ChatMessage>> = Result.success(emptyList())
    override fun sendChatMessageStream(
      conversationId: String,
      message: String,
      attachments: List<Attachment>,
      isTemporary: Boolean,
      clientMessageId: String
    ): Flow<ChatStreamEvent> = emptyFlow()
    override suspend fun uploadAttachment(attachment: Attachment): Result<Attachment> = Result.success(attachment)
    override suspend fun getPersonalization(): Result<PersonalizationSettings> = Result.success(PersonalizationSettings())
    override suspend fun updatePersonalization(settings: PersonalizationSettings): Result<Unit> = Result.success(Unit)
    override suspend fun getMemories(): Result<List<MemoryItem>> = Result.success(emptyList())
    override suspend fun addMemory(content: String): Result<MemoryItem> = Result.success(MemoryItem(id = "m1", content = content))
    override suspend fun deleteMemory(memoryId: String): Result<Unit> = Result.success(Unit)
  }

  @Test
  fun `login updates session in repository`() = runBlocking {
    val authRepo = TrawaAuthRepository(fakeApiClient)
    assertNull(authRepo.sessionState.value)

    val success = authRepo.login("test@trawa.ai", "password123")
    assertTrue(success.isSuccess)
    assertNotNull(authRepo.sessionState.value)
    assertEquals("test@trawa.ai", authRepo.sessionState.value?.user?.email)

    authRepo.logout()
    assertNull(authRepo.sessionState.value)
  }

  @Test
  fun `temporary chat toggling creates isolated state`() = runBlocking {
    val chatRepo = TrawaChatRepository(fakeApiClient)
    assertFalse(chatRepo.isTemporaryChat.value)

    val isTempNow = chatRepo.toggleTemporaryChat(true)
    assertTrue(isTempNow)
    assertTrue(chatRepo.isTemporaryChat.value)
    assertTrue(chatRepo.activeConversationId.value?.startsWith("temp-") == true)

    val turnedOff = chatRepo.toggleTemporaryChat(false)
    assertFalse(turnedOff)
    assertFalse(chatRepo.isTemporaryChat.value)
  }

  @Test
  fun `attachment repository staging and removal`() {
    val attRepo = TrawaAttachmentRepository(fakeApiClient)
    assertEquals(0, attRepo.stagedAttachments.value.size)

    val item = Attachment(
      id = "att_1",
      type = AttachmentType.IMAGE,
      name = "photo.jpg",
      sizeBytes = 1024
    )
    attRepo.addAttachment(item)
    assertEquals(1, attRepo.stagedAttachments.value.size)
    assertEquals("photo.jpg", attRepo.stagedAttachments.value.first().name)

    attRepo.removeAttachment("att_1")
    assertEquals(0, attRepo.stagedAttachments.value.size)
  }
}
