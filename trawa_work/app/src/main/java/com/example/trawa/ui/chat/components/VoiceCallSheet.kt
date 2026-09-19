package com.example.trawa.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trawa.domain.model.VoiceSessionState
import com.example.trawa.ui.components.TrawaEmblem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallSheet(
  voiceState: VoiceSessionState,
  liveTranscript: String,
  onInterrupt: () -> Unit,
  onClose: () -> Unit,
  onToggleMic: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "aura_scale"
  )

  ModalBottomSheet(
    onDismissRequest = onClose,
    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    containerColor = TrawaDarkBackground,
    contentColor = TrawaTextPrimaryDark,
    dragHandle = { BottomSheetDefaults.DragHandle() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "3AR V1 Pro Voice Call",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        ),
        color = TrawaCyan
      )

      Spacer(modifier = Modifier.height(36.dp))

      // Central Pulsing Orb with TRAWA Emblem
      Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
      ) {
        // Glowing animated aura
        if (voiceState == VoiceSessionState.LISTENING || voiceState == VoiceSessionState.SPEAKING) {
          Box(
            modifier = Modifier
              .size(140.dp)
              .scale(pulseScale)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  colors = listOf(
                    if (voiceState == VoiceSessionState.SPEAKING) TrawaElectricBlue.copy(alpha = 0.4f) else TrawaCyan.copy(alpha = 0.4f),
                    Color.Transparent
                  )
                )
              )
          )
        }

        Surface(
          modifier = Modifier.size(100.dp),
          shape = CircleShape,
          color = TrawaDarkSurfaceVariant,
          border = androidx.compose.foundation.BorderStroke(2.dp, TrawaCyan)
        ) {
          Box(contentAlignment = Alignment.Center) {
            TrawaEmblem(size = 54.dp)
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Status text
      val statusLabel = when (voiceState) {
        VoiceSessionState.LISTENING -> "Listening to you..."
        VoiceSessionState.PROCESSING -> "3AR V1 Pro thinking..."
        VoiceSessionState.SPEAKING -> "3AR V1 Pro speaking..."
        VoiceSessionState.ERROR -> "Voice unavailable on this device."
        VoiceSessionState.IDLE -> "Ready"
      }

      Text(
        text = statusLabel,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        color = TrawaTextPrimaryDark
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Live transcript snippet
      Text(
        text = if (liveTranscript.isNotBlank()) "\"$liveTranscript\"" else "Speak naturally. 3AR V1 Pro will respond intelligently.",
        style = MaterialTheme.typography.bodyMedium,
        color = TrawaTextSecondaryDark,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .heightIn(min = 40.dp)
      )

      Spacer(modifier = Modifier.height(36.dp))

      // Action Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Mute/Mic Toggle
        IconButton(
          onClick = onToggleMic,
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(TrawaDarkSurface)
        ) {
          Icon(
            imageVector = if (voiceState == VoiceSessionState.LISTENING) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = "Toggle microphone",
            tint = TrawaTextPrimaryDark
          )
        }

        // Barge-in / Interruption Button
        if (voiceState == VoiceSessionState.SPEAKING) {
          Button(
            onClick = onInterrupt,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White,
              contentColor = Color.Black
            )
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Interrupt", color = Color.Black, fontWeight = FontWeight.Black)
          }
        }

        // End Call Button
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(TrawaError)
            .testTag("end_voice_call_button")
        ) {
          Icon(
            imageVector = Icons.Default.CallEnd,
            contentDescription = "End Call",
            tint = Color.White
          )
        }
      }
    }
  }
}
