package com.example.trawa.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A sleek, compact floating strip showing strictly the 3 icons (Camera, Gallery/Images, Files)
 * directly above the `+` button, matching user's exact specification.
 */
@Composable
fun AttachmentActionStrip(
  visible: Boolean,
  onDismiss: () -> Unit,
  onPickCamera: () -> Unit,
  onPickImages: () -> Unit,
  onPickFiles: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    modifier = modifier
  ) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val stripBg = if (isDarkTheme) Color(0xFF18181B) else Color(0xFFFFFFFF)
    val stripBorder = if (isDarkTheme) Color(0xFF27272A) else Color(0xFFE4E4E7)
    val iconTint = if (isDarkTheme) Color.White else Color(0xFF09090B)

    Surface(
      shape = RoundedCornerShape(20.dp),
      color = stripBg,
      border = androidx.compose.foundation.BorderStroke(1.dp, stripBorder),
      shadowElevation = 6.dp,
      modifier = Modifier.padding(bottom = 6.dp, start = 8.dp)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Camera Icon
        IconButton(
          onClick = {
            onDismiss()
            onPickCamera()
          },
          modifier = Modifier
            .size(38.dp)
            .testTag("attachment_strip_camera")
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Camera",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
          )
        }

        // 2. Images / Gallery Icon
        IconButton(
          onClick = {
            onDismiss()
            onPickImages()
          },
          modifier = Modifier
            .size(38.dp)
            .testTag("attachment_strip_images")
        ) {
          Icon(
            imageVector = Icons.Default.Image,
            contentDescription = "Photos",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
          )
        }

        // 3. Files / Documents Icon
        IconButton(
          onClick = {
            onDismiss()
            onPickFiles()
          },
          modifier = Modifier
            .size(38.dp)
            .testTag("attachment_strip_files")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = "Documents",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}
