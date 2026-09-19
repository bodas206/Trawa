package com.example.trawa.data.repository

import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ProfileRepository {
  val personalization: StateFlow<PersonalizationSettings>
  val memories: StateFlow<List<MemoryItem>>
  val isDarkTheme: StateFlow<Boolean>
  val networkConfig: StateFlow<NetworkConfig>

  suspend fun loadPersonalization(): Result<PersonalizationSettings>
  suspend fun savePersonalization(settings: PersonalizationSettings): Result<Unit>
  suspend fun loadMemories(): Result<List<MemoryItem>>
  suspend fun addMemory(content: String): Result<MemoryItem>
  suspend fun deleteMemory(memoryId: String): Result<Unit>
  fun setThemeMode(isDark: Boolean)
  fun updateApiBaseUrl(newUrl: String): Result<Unit>
}

class TrawaProfileRepository(
  private val apiClient: TrawaApiClient
) : ProfileRepository {

  private val _personalization = MutableStateFlow(PersonalizationSettings())
  override val personalization: StateFlow<PersonalizationSettings> = _personalization.asStateFlow()

  private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
  override val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

  private val _isDarkTheme = MutableStateFlow(true)
  override val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

  private val _networkConfig = MutableStateFlow(NetworkConfig(apiBaseUrl = apiClient.getBaseUrl()))
  override val networkConfig: StateFlow<NetworkConfig> = _networkConfig.asStateFlow()

  override suspend fun loadPersonalization(): Result<PersonalizationSettings> {
    val result = apiClient.getPersonalization()
    result.onSuccess { _personalization.value = it }
    return result
  }

  override suspend fun savePersonalization(settings: PersonalizationSettings): Result<Unit> {
    val result = apiClient.updatePersonalization(settings)
    if (result.isSuccess) {
      _personalization.value = settings
    }
    return result
  }

  override suspend fun loadMemories(): Result<List<MemoryItem>> {
    val result = apiClient.getMemories()
    result.onSuccess { _memories.value = it }
    return result
  }

  override suspend fun addMemory(content: String): Result<MemoryItem> {
    val result = apiClient.addMemory(content)
    result.onSuccess { item ->
      _memories.value = _memories.value + item
    }
    return result
  }

  override suspend fun deleteMemory(memoryId: String): Result<Unit> {
    val result = apiClient.deleteMemory(memoryId)
    if (result.isSuccess) {
      _memories.value = _memories.value.filter { it.id != memoryId }
    }
    return result
  }

  override fun setThemeMode(isDark: Boolean) {
    _isDarkTheme.value = isDark
  }

  override fun updateApiBaseUrl(newUrl: String): Result<Unit> {
    return try {
      apiClient.setBaseUrl(newUrl)
      _networkConfig.value = NetworkConfig(
        environment = if (newUrl.contains("staging")) AppEnvironment.STAGING else AppEnvironment.PRODUCTION,
        apiBaseUrl = apiClient.getBaseUrl()
      )
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
