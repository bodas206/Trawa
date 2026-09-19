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
  data class AwaitingOtp(val email: String, val name: String, val isSignUp: Boolean) : AuthUiState
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

  fun requestOtp(email: String, name: String, isSignUp: Boolean) {
    val effectiveEmail = email.trim()
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(effectiveEmail).matches()) {
      _uiState.value = AuthUiState.Error("Please enter a valid email address.")
      return
    }
    if (isSignUp && name.trim().isBlank()) {
      _uiState.value = AuthUiState.Error("Please enter your name.")
      return
    }
    viewModelScope.launch {
      _uiState.value = AuthUiState.Loading("Sending your 6-digit verification code...")
      val result = authRepository.sendOtp(effectiveEmail)
      result.fold(
        onSuccess = { _uiState.value = AuthUiState.AwaitingOtp(effectiveEmail, name.trim(), isSignUp) },
        onFailure = { error -> _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Could not send verification code.") }
      )
    }
  }

  fun verifyOtp(email: String, code: String, name: String, isSignUp: Boolean) {
    val effectiveCode = code.trim()
    if (!Regex("^[0-9]{6}$").matches(effectiveCode)) {
      _uiState.value = AuthUiState.Error("Enter the 6-digit verification code.")
      return
    }
    viewModelScope.launch {
      _uiState.value = AuthUiState.Loading("Verifying your TRAWA account...")
      val result = authRepository.verifyOtp(email.trim(), effectiveCode, name.trim())
      result.fold(
        onSuccess = { session -> _uiState.value = AuthUiState.Authenticated(session) },
        onFailure = { error -> _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Invalid or expired verification code.") }
      )
    }
  }

  fun resendOtp(email: String) {
    val current = _uiState.value as? AuthUiState.AwaitingOtp
    viewModelScope.launch {
      _uiState.value = AuthUiState.Loading("Sending a new verification code...")
      val result = authRepository.sendOtp(email.trim())
      result.fold(
        onSuccess = { _uiState.value = AuthUiState.AwaitingOtp(email.trim(), current?.name.orEmpty(), current?.isSignUp ?: false) },
        onFailure = { error -> _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Could not resend verification code.") }
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

  fun useDifferentEmail() {
    _uiState.value = AuthUiState.Unauthenticated
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
