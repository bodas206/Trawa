package com.example.trawa.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trawa.data.repository.ProfileRepository
import com.example.trawa.domain.model.MemoryItem
import com.example.trawa.domain.model.PersonalizationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
  private val profileRepository: ProfileRepository
) : ViewModel() {

  val personalization = profileRepository.personalization
  val memories = profileRepository.memories
  val isDarkTheme = profileRepository.isDarkTheme
  val networkConfig = profileRepository.networkConfig

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  init {
    viewModelScope.launch {
      profileRepository.loadPersonalization()
      profileRepository.loadMemories()
    }
  }

  fun updatePersonalization(instructions: String, language: String, tone: String) {
    viewModelScope.launch {
      val newSettings = PersonalizationSettings(
        customInstructions = instructions,
        preferredLanguage = language,
        responseTone = tone
      )
      val result = profileRepository.savePersonalization(newSettings)
      result.fold(
        onSuccess = { _feedbackMessage.value = "Personalization saved successfully." },
        onFailure = { _feedbackMessage.value = "Could not save to backend: ${it.localizedMessage}" }
      )
    }
  }

  fun addMemory(content: String) {
    if (content.isBlank()) return
    viewModelScope.launch {
      val result = profileRepository.addMemory(content.trim())
      result.fold(
        onSuccess = { _feedbackMessage.value = "Memory saved to 3AR V1 Pro." },
        onFailure = { _feedbackMessage.value = "Failed to add memory: ${it.localizedMessage}" }
      )
    }
  }

  fun deleteMemory(id: String) {
    viewModelScope.launch {
      profileRepository.deleteMemory(id)
    }
  }

  fun toggleTheme(isDark: Boolean) {
    profileRepository.setThemeMode(isDark)
  }

  fun updateServerUrl(url: String) {
    val result = profileRepository.updateApiBaseUrl(url)
    result.fold(
      onSuccess = { _feedbackMessage.value = "Server endpoint updated to ${networkConfig.value.apiBaseUrl}" },
      onFailure = { _feedbackMessage.value = it.message }
    )
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }
}
