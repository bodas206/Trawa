package com.example.trawa.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.trawa.data.network.TrawaApiClient
import com.example.trawa.domain.model.Attachment
import com.example.trawa.domain.model.AttachmentType
import com.example.trawa.domain.model.UploadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface AttachmentRepository {
  val stagedAttachments: StateFlow<List<Attachment>>
  fun addAttachment(attachment: Attachment)
  fun addFromUri(context: Context, uri: Uri, type: AttachmentType)
  fun removeAttachment(id: String)
  fun clearStaged()
  suspend fun uploadAllStaged(): List<Attachment>
  suspend fun uploadAttachments(attachments: List<Attachment>): List<Attachment>
}

class TrawaAttachmentRepository(
  private val apiClient: TrawaApiClient
) : AttachmentRepository {

  private val _stagedAttachments = MutableStateFlow<List<Attachment>>(emptyList())
  override val stagedAttachments: StateFlow<List<Attachment>> = _stagedAttachments.asStateFlow()

  override fun addAttachment(attachment: Attachment) {
    _stagedAttachments.value = _stagedAttachments.value + attachment
  }

  override fun addFromUri(context: Context, uri: Uri, type: AttachmentType) {
    var fileName = "attachment_${System.currentTimeMillis()}"
    var fileSize = 0L
    val mimeType = context.contentResolver.getType(uri) ?: "*/*"

    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
          if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
          if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
        }
      }
    } catch (_: Exception) {}

    val attachment = Attachment(
      id = UUID.randomUUID().toString(),
      type = type,
      name = fileName,
      sizeBytes = fileSize,
      mimeType = mimeType,
      localUri = uri.toString(),
      uploadState = UploadState.PENDING
    )
    addAttachment(attachment)
  }

  override fun removeAttachment(id: String) {
    _stagedAttachments.value = _stagedAttachments.value.filter { it.id != id }
  }

  override fun clearStaged() {
    _stagedAttachments.value = emptyList()
  }

  override suspend fun uploadAllStaged(): List<Attachment> = uploadAttachments(_stagedAttachments.value).also { clearStaged() }

  override suspend fun uploadAttachments(attachments: List<Attachment>): List<Attachment> {
    if (attachments.isEmpty()) return emptyList()
    return attachments.map { att ->
      apiClient.uploadAttachment(att).getOrElse { throw it }
    }
  }
}
