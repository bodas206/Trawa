package com.example.trawa.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 3AR V1 Pro Activity Animation
 *
 * Subtle, elegant, non-technical native activity animation.
 * Replaces technical text (like "Searching web", "Syncing", "Thinking").
 */
@Composable
fun ThreeArActivityWave(
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "3ar_activity_wave")

  val pulseProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "wave_progress"
  )

  Row(
    modifier = modifier
      .padding(vertical = 6.dp, horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    repeat(3) { index ->
      val delayOffset = index * 0.25f
      val animatedAlpha = ((pulseProgress + delayOffset) % 1f).let { value ->
        0.25f + 0.75f * kotlin.math.sin(value * Math.PI.toFloat())
      }
      val animatedHeight = 6.dp + (8.dp * animatedAlpha)

      Box(
        modifier = Modifier
          .width(4.dp)
          .height(animatedHeight)
          .clip(RoundedCornerShape(100.dp))
          .background(Color.White.copy(alpha = animatedAlpha.coerceIn(0.2f, 1f)))
      )
    }
  }
}

/**
 * Minimal subtle indicator dot for the message header
 */
@Composable
fun ThreeArActivityDot(
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "3ar_dot")
  val alpha by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "dot_alpha"
  )

  Box(
    modifier = modifier
      .size(7.dp)
      .clip(CircleShape)
      .background(Color.White.copy(alpha = alpha))
  )
}
