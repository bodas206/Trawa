package com.example.trawa.domain.model

import java.time.Instant

data class Conversation(
  val id: String,
  val title: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isTemporary: Boolean = false,
  val lastMessagePreview: String = ""
)
