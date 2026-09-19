package com.example.trawa.domain.model

data class User(
  val id: String,
  val email: String,
  val displayName: String = "User",
  val plan: String = "TRAWA Pro"
)

data class Session(
  val token: String,
  val user: User,
  val expiresAt: Long,
  val refreshToken: String? = null
)
