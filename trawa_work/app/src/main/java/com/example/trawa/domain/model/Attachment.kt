package com.example.trawa.domain.model

enum class AttachmentType {
  IMAGE,
  FILE,
  AUDIO,
  VIDEO,
  CAMERA_CAPTURE,
  SCREEN_CAPTURE
}

enum class UploadState {
  PENDING,
  UPLOADING,
  UPLOADED,
  FAILED
}

data class Attachment(
  val id: String,
  val type: AttachmentType,
  val name: String,
  val sizeBytes: Long = 0,
  val mimeType: String = "*/*",
  val localUri: String? = null,
  val remoteId: String? = null,
  val uploadState: UploadState = UploadState.PENDING,
  val uploadProgress: Float = 0f,
  val metadata: Map<String, String> = emptyMap(),
  val base64Content: String? = null
)
