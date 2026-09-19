package com.example.trawa.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trawa.domain.model.Attachment
import com.example.trawa.domain.model.AttachmentType
import com.example.trawa.domain.model.VoiceSessionState
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerView(
  text: String,
  onTextChanged: (String) -> Unit,
  onSend: () -> Unit,
  onOpenAttachmentMenu: () -> Unit,
  onMicClick: () -> Unit,
  onCallClick: () -> Unit,
  voiceState: VoiceSessionState,
  stagedAttachments: List<Attachment>,
  onRemoveAttachment: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val isListening = voiceState == VoiceSessionState.LISTENING
  val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

  // Colors for Black & White pristine monochrome palette
  val barBackground = if (isDarkTheme) Color(0xFF141417) else Color(0xFFF4F4F5)
  val barBorder = if (isDarkTheme) Color(0xFF27272A) else Color(0xFFE4E4E7)
  val textColor = if (isDarkTheme) Color(0xFFF4F4F5) else Color(0xFF09090B)
  val hintColor = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A)
  val iconTint = if (isDarkTheme) Color(0xFFD4D4D8) else Color(0xFF3F3F46)
  val sendBg = if (isDarkTheme) Color.White else Color(0xFF09090B)
  val sendIconTint = if (isDarkTheme) Color(0xFF09090B) else Color.White

  val isImeVisible = WindowInsets.isImeVisible

  Column(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(
        if (isImeVisible) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars
      )
      .padding(horizontal = 12.dp, vertical = if (isImeVisible) 4.dp else 8.dp)
  ) {
    // Staged Attachments Row
    if (stagedAttachments.isNotEmpty()) {
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(stagedAttachments, key = { it.id }) { att ->
          StagedAttachmentChip(
            attachment = att,
            onRemove = { onRemoveAttachment(att.id) },
            isDarkTheme = isDarkTheme
          )
        }
      }
    }

    // Slim, flexible Composer Bar Surface
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, barBorder, RoundedCornerShape(22.dp)),
      shape = RoundedCornerShape(22.dp),
      color = barBackground,
      tonalElevation = 1.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left: `+` Attachment Button
        IconButton(
          onClick = onOpenAttachmentMenu,
          modifier = Modifier
            .size(36.dp)
            .testTag("composer_plus_button")
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Attach media or files",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
          )
        }

        // Center: Input Field with "Ask 3AR V1 Pro" hint
        Box(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 6.dp, vertical = 6.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          if (text.isEmpty()) {
            Text(
              text = "Ask 3AR V1 Pro",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
              ),
              color = hintColor
            )
          }
          BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            textStyle = TextStyle(
              color = textColor,
              fontSize = 15.sp,
              lineHeight = 21.sp,
              fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(if (isDarkTheme) Color.White else Color.Black),
            maxLines = 5,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("composer_text_input")
          )
        }

        // Right side controls: If text or attachments exist -> Send button. Else -> Call and Mic buttons
        if (text.isNotBlank() || stagedAttachments.isNotEmpty()) {
          IconButton(
            onClick = onSend,
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(sendBg)
              .testTag("composer_send_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send message",
              tint = sendIconTint,
              modifier = Modifier.size(17.dp)
            )
          }
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            // Call Button
            IconButton(
              onClick = onCallClick,
              modifier = Modifier
                .size(34.dp)
                .testTag("composer_call_button")
            ) {
              Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Start voice call with 3AR V1 Pro",
                tint = iconTint,
                modifier = Modifier.size(18.dp)
              )
            }

            // Microphone Button
            IconButton(
              onClick = onMicClick,
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isListening) (if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)) else Color.Transparent)
                .testTag("composer_mic_button")
            ) {
              Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = if (isListening) "Stop voice input" else "Start speech input",
                tint = if (isListening) (if (isDarkTheme) Color.White else Color.Black) else iconTint,
                modifier = Modifier.size(19.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StagedAttachmentChip(
  attachment: Attachment,
  onRemove: () -> Unit,
  isDarkTheme: Boolean
) {
  val chipBg = if (isDarkTheme) Color(0xFF1F1F24) else Color(0xFFE4E4E7)
  val chipBorder = if (isDarkTheme) Color(0xFF2E2E36) else Color(0xFFD4D4D8)
  val chipText = if (isDarkTheme) Color(0xFFF4F4F5) else Color(0xFF09090B)

  Surface(
    shape = RoundedCornerShape(10.dp),
    color = chipBg,
    border = BorderStroke(1.dp, chipBorder)
  ) {
    Row(
      modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = when (attachment.type) {
          AttachmentType.IMAGE -> Icons.Default.Image
          AttachmentType.CAMERA_CAPTURE -> Icons.Default.PhotoCamera
          else -> Icons.AutoMirrored.Filled.InsertDriveFile
        },
        contentDescription = null,
        tint = if (isDarkTheme) Color.White else Color.Black,
        modifier = Modifier.size(15.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = attachment.name,
        style = MaterialTheme.typography.labelSmall.copy(color = chipText),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 130.dp)
      )
      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(24.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Remove attachment",
          tint = if (isDarkTheme) Color(0xFFA1A1AA) else Color(0xFF71717A),
          modifier = Modifier.size(13.dp)
        )
      }
    }
  }
}
