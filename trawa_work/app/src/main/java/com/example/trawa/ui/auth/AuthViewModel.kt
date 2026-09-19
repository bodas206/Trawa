package com.example.trawa.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trawa.data.repository.AuthRepository
import com.example.trawa.domain.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
  data object CheckingSession : AuthUiState
  data object Unauthenticated : AuthUiState
  data class Authenticated(val session: Session) : AuthUiState
  data class Loading(val message: String = "Signing in to TRAWA...") : AuthUiState
  data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
  private val authRepository: AuthRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.CheckingSession)
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private val _forgotPasswordMessage = MutableStateFlow<String?>(null)
  val forgotPasswordMessage: StateFlow<String?> = _forgotPasswordMessage.asStateFlow()

  private val _isResettingPassword = MutableStateFlow(false)
  val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()

  init {
    checkCurrentSession()
  }

  fun checkCurrentSession() {
    viewModelScope.launch {
      _uiState.value = AuthUiState.CheckingSession
      val session = authRepository.checkExistingSession()
      if (session != null) {
        _uiState.value = AuthUiState.Authenticated(session)
      } else {
        _uiState.value = AuthUiState.Unauthenticated
      }
    }
  }

  fun login(email: String, pass: String) {
    val effectiveEmail = email.trim()
    val effectivePass = pass

    viewModelScope.launch {
      _uiState.value = AuthUiState.Loading()
      val result = authRepository.login(effectiveEmail, effectivePass)
      result.fold(
        onSuccess = { session ->
          _uiState.value = AuthUiState.Authenticated(session)
        },
        onFailure = { error ->
          _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Authentication failed. Please check connection.")
        }
      )
    }
  }

  fun signUp(email: String, pass: String, name: String) {
    val effectiveEmail = email.trim()
    val effectivePass = pass
    if (effectiveEmail.isBlank() || effectivePass.isBlank()) {
      _uiState.value = AuthUiState.Error("Email and password are required.")
      return
    }
    viewModelScope.launch {
      _uiState.value = AuthUiState.Loading("Creating your TRAWA account...")
      val result = authRepository.signUp(effectiveEmail, effectivePass, name.trim())
      result.fold(
        onSuccess = { session ->
          if (session != null) _uiState.value = AuthUiState.Authenticated(session)
          else _uiState.value = AuthUiState.Error("Account created. Check your email to confirm the account, then sign in.")
        },
        onFailure = { error -> _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Could not create account.") }
      )
    }
  }

  fun resetPassword(email: String) {
    if (email.isBlank()) {
      _forgotPasswordMessage.value = "Please enter your account email address."
      return
    }
    viewModelScope.launch {
      _isResettingPassword.value = true
      val result = authRepository.resetPassword(email.trim())
      _isResettingPassword.value = false
      result.fold(
        onSuccess = {
          _forgotPasswordMessage.value = "Password reset instructions sent to $email."
        },
        onFailure = { error ->
          _forgotPasswordMessage.value = error.localizedMessage ?: "Failed to send reset link."
        }
      )
    }
  }

  fun clearError() {
    if (_uiState.value is AuthUiState.Error) {
      _uiState.value = AuthUiState.Unauthenticated
    }
    _forgotPasswordMessage.value = null
  }

  fun logout() {
    viewModelScope.launch {
      authRepository.logout()
      _uiState.value = AuthUiState.Unauthenticated
    }
  }
}
