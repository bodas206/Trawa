package com.example.trawa.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.trawa.domain.model.*
import com.example.trawa.ui.components.TrawaEmblem
import com.example.ui.theme.*

@Composable
fun ChatMessageItem(
  message: ChatMessage,
  isStreaming: Boolean,
  onEditMessage: (ChatMessage, String) -> Unit,
  onRegenerate: (ChatMessage) -> Unit,
  onReadAloud: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  when (message.role) {
    MessageRole.USER -> {
      UserMessageBubble(
        message = message,
        onEditMessage = onEditMessage,
        onCopy = { copyText(context, message.content) },
        onShare = { shareText(context, message.content) },
        modifier = modifier
      )
    }
    MessageRole.ASSISTANT -> {
      AssistantMessageItem(
        message = message,
        isStreamingThis = isStreaming && message.status == MessageStatus.STREAMING,
        onRegenerate = { onRegenerate(message) },
        onReadAloud = { onReadAloud(message.content) },
        onCopy = { copyText(context, message.content) },
        onShare = { shareText(context, message.content) },
        modifier = modifier
      )
    }
    else -> {}
  }
}

@Composable
private fun UserMessageBubble(
  message: ChatMessage,
  onEditMessage: (ChatMessage, String) -> Unit,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showEditDialog by remember { mutableStateOf(false) }
  var editedText by remember { mutableStateOf(message.content) }
  var showActionRow by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    horizontalAlignment = Alignment.End
  ) {
    // Staged attachments preview if any
    if (message.attachments.isNotEmpty()) {
      Row(
        modifier = Modifier
          .padding(bottom = 6.dp)
          .wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        message.attachments.forEach { att ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (att.type == AttachmentType.IMAGE) Icons.Default.Image else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = TrawaCyan
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = att.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bubbleBg = if (isDarkTheme) Color.White else Color(0xFF141417)
    val bubbleTextColor = if (isDarkTheme) Color(0xFF0A0A0C) else Color(0xFFF4F4F5)

    // User Text Bubble - dynamic sizing strictly matching the word/text length ("على قد الكلمة")
    Surface(
      shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
      color = bubbleBg,
      contentColor = bubbleTextColor,
      modifier = Modifier
        .widthIn(min = 32.dp, max = 320.dp)
        .clickable { showActionRow = !showActionRow }
        .testTag("user_message_bubble")
    ) {
      SelectionContainer {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
          val isArabic = message.content.any { it in '\u0600'..'\u06FF' }
          Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge.copy(
              fontSize = 15.sp,
              lineHeight = 22.sp,
              textDirection = if (isArabic) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
            ),
            color = bubbleTextColor
          )
        }
      }
    }

    // Contextual Actions on click/long-press
    AnimatedVisibility(
      visible = showActionRow,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Row(
        modifier = Modifier
          .padding(top = 4.dp, end = 4.dp)
          .wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        IconButton(
          onClick = {
            onCopy()
            showActionRow = false
          },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy message", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
          onClick = {
            onShare()
            showActionRow = false
          },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.Share, contentDescription = "Share message", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
          onClick = {
            editedText = message.content
            showEditDialog = true
            showActionRow = false
          },
          modifier = Modifier.size(32.dp)
        ) {
          Icon(Icons.Default.Edit, contentDescription = "Edit message", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  // Edit dialog
  if (showEditDialog) {
    AlertDialog(
      onDismissRequest = { showEditDialog = false },
      title = { Text("Edit Message") },
      text = {
        OutlinedTextField(
          value = editedText,
          onValueChange = { editedText = it },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
          shape = RoundedCornerShape(12.dp)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showEditDialog = false
            onEditMessage(message, editedText)
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
          )
        ) {
          Text("Send & Regenerate", fontWeight = FontWeight.Black)
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditDialog = false }) {
          Text("Cancel")
        }
      },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp)
    )
  }
}

@Composable
private fun AssistantMessageItem(
  message: ChatMessage,
  isStreamingThis: Boolean,
  onRegenerate: () -> Unit,
  onReadAloud: () -> Unit,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.Start
  ) {
    // Assistant Header: 3AR V1 Pro emblem + label
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(bottom = 6.dp)
    ) {
      TrawaEmblem(size = 24.dp)
      Text(
        text = "3AR V1 Pro",
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.6.sp
        ),
        color = TrawaCyan
      )
    }

    // Message Body
    SelectionContainer {
      Column(modifier = Modifier.padding(start = 32.dp, end = 12.dp)) {
        if (message.content.isNotBlank()) {
          val imgRegex = Regex("!\\[(.*?)\\]\\((https?://.*?)\\)")
          val imgMatch = imgRegex.find(message.content)
          if (imgMatch != null) {
            val beforeText = message.content.substring(0, imgMatch.range.first).trim()
            val altText = imgMatch.groupValues[1]
            val imageUrl = imgMatch.groupValues[2]
            val afterText = message.content.substring(imgMatch.range.last + 1).trim()

            if (beforeText.isNotEmpty()) {
              BidiFormattedContent(
                content = beforeText,
                primaryTextColor = MaterialTheme.colorScheme.onSurface,
                accentColor = Color.White
              )
              Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
            ) {
              AsyncImage(
                model = imageUrl,
                contentDescription = altText.ifBlank { "Generated Image" },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
              )
            }

            if (afterText.isNotEmpty()) {
              Spacer(modifier = Modifier.height(8.dp))
              BidiFormattedContent(
                content = afterText,
                primaryTextColor = MaterialTheme.colorScheme.onSurface,
                accentColor = Color.White
              )
            }
          } else {
            BidiFormattedContent(
              content = message.content,
              primaryTextColor = MaterialTheme.colorScheme.onSurface,
              accentColor = Color.White
            )
          }
        } else if (isStreamingThis) {
          ThreeArActivityWave(modifier = Modifier.padding(vertical = 4.dp))
        }

        // Error message card if failed
        if (message.status == MessageStatus.ERROR && message.errorMessage != null) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = TrawaError.copy(alpha = 0.12f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
          ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = TrawaError, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = message.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TrawaError
              )
            }
          }
        }

    }

    // Assistant Action Bar
    if (!isStreamingThis && message.content.isNotBlank()) {
      Row(
        modifier = Modifier
          .padding(start = 28.dp, top = 6.dp)
          .wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onReadAloud,
          modifier = Modifier
            .size(32.dp)
            .testTag("action_read_aloud")
        ) {
          Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
          onClick = onCopy,
          modifier = Modifier
            .size(32.dp)
            .testTag("action_copy_message")
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
          onClick = onShare,
          modifier = Modifier
            .size(32.dp)
            .testTag("action_share_message")
        ) {
          Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
          onClick = onRegenerate,
          modifier = Modifier
            .size(32.dp)
            .testTag("action_regenerate_message")
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun FormattedContent(content: String) {
  // Check if content contains code blocks
  if (content.contains("```")) {
    val parts = content.split("```")
    parts.forEachIndexed { index, part ->
      if (index % 2 == 1) {
        // Code Block
        val lines = part.trim().lines()
        val lang = if (lines.isNotEmpty() && !lines.first().contains(" ")) lines.first() else ""
        val codeBody = if (lang.isNotEmpty()) lines.drop(1).joinToString("\n") else part.trim()
        val context = LocalContext.current

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF030D1E),
          border = BorderStroke(1.dp, TrawaDarkBorder),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            if (lang.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(text = lang, style = MaterialTheme.typography.labelSmall, color = TrawaCyan)
                Text(
                  text = "Copy",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.clickable { copyText(context, codeBody) }
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
              text = codeBody,
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              color = TrawaIceCyan
            )
          }
        }
      } else if (part.isNotBlank()) {
        Text(
          text = part,
          style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(vertical = 2.dp)
        )
      }
    }
  } else {
    Text(
      text = content,
      style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 23.sp),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun StreamingPulseDot() {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val alpha by infiniteTransition.animateFloat(
    initialValue = 0.2f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "dot_alpha"
  )

  Box(
    modifier = Modifier
      .size(8.dp)
      .clip(CircleShape)
      .background(TrawaCyan.copy(alpha = alpha))
  )
}

private fun copyText(context: Context, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = ClipData.newPlainText("TRAWA Chat", text)
  clipboard.setPrimaryClip(clip)
  Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
  val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
  }
  context.startActivity(Intent.createChooser(intent, "Share via"))
}
