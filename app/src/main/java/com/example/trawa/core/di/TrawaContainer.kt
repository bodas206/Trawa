package com.example.trawa.core.di

import android.content.Context
import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.data.network.TrawaApiClientImpl
import com.example.trawa.data.repository.*

class TrawaContainer(context: Context) {
  val apiClient: TrawaApiClient = TrawaApiClientImpl(context.applicationContext)
  val authRepository: AuthRepository = TrawaAuthRepository(apiClient)
  val chatRepository: ChatRepository = TrawaChatRepository(apiClient)
  val profileRepository: ProfileRepository = TrawaProfileRepository(apiClient)
  val attachmentRepository: AttachmentRepository = TrawaAttachmentRepository(apiClient)
  val voiceService: VoiceService = TrawaVoiceService(context.applicationContext)
}
