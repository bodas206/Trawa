package com.example.trawa.data.repository

import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.domain.model.Session
import com.example.trawa.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthRepository {
  val sessionState: StateFlow<Session?>
  suspend fun login(email: String, password: String): Result<Session>
  suspend fun signUp(email: String, password: String, name: String): Result<Session?>
  suspend fun logout(): Result<Unit>
  suspend fun checkExistingSession(): Session?
  suspend fun resetPassword(email: String): Result<Unit>
}

class TrawaAuthRepository(
  private val apiClient: TrawaApiClient
) : AuthRepository {

  private val _sessionState = MutableStateFlow<Session?>(null)
  override val sessionState: StateFlow<Session?> = _sessionState.asStateFlow()

  override suspend fun login(email: String, password: String): Result<Session> {
    val result = apiClient.login(email, password)
    result.onSuccess { session ->
      _sessionState.value = session
    }
    return result
  }

  override suspend fun signUp(email: String, password: String, name: String): Result<Session?> {
    val result = apiClient.signUp(email, password, name)
    result.getOrNull()?.let { _sessionState.value = it }
    return result
  }

  override suspend fun logout(): Result<Unit> {
    val result = apiClient.logout()
    _sessionState.value = null
    return result
  }

  override suspend fun checkExistingSession(): Session? {
    val session = apiClient.getCurrentSession()
    _sessionState.value = session
    return session
  }

  override suspend fun resetPassword(email: String): Result<Unit> {
    return apiClient.resetPassword(email)
  }
}
