package com.example.trawa.domain.model

enum class ArtifactType {
  GENERATED_IMAGE,
  GENERATED_FILE
}

data class GeneratedArtifact(
  val id: String,
  val type: ArtifactType,
  val title: String,
  val mimeType: String,
  val contentUrl: String? = null,
  val rawTextContent: String? = null,
  val sizeBytes: Long = 0,
  val isGenerating: Boolean = false,
  val errorMessage: String? = null
)
