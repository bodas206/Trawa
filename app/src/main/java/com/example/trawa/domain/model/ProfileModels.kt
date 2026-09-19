package com.example.trawa.domain.model

data class MemoryItem(
  val id: String,
  val content: String,
  val createdAt: Long = System.currentTimeMillis(),
  val category: String = "general"
)

data class PersonalizationSettings(
  val customInstructions: String = "",
  val preferredLanguage: String = "Auto (Default)",
  val responseTone: String = "Balanced & Precise"
)

enum class VoiceSessionState {
  IDLE,
  LISTENING,
  PROCESSING,
  SPEAKING,
  ERROR
}

enum class AppEnvironment {
  PRODUCTION,
  STAGING,
  DEVELOPMENT
}

data class NetworkConfig(
  val environment: AppEnvironment = AppEnvironment.PRODUCTION,
  val apiBaseUrl: String = DEFAULT_PRODUCTION_URL
) {
  companion object {
    const val DEFAULT_PRODUCTION_URL = "https://api.trawa.ai"
    const val DEFAULT_STAGING_URL = "https://staging-api.trawa.ai"
  }
}
