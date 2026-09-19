package com.example.trawa.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.trawa.ui.components.TrawaEmblem

/**
 * Very short first-run brand reveal. It is intentionally not shown on every
 * activity recreation or app launch after the first successful boot.
 */
@Composable
fun TrawaSplashScreen(onFinished: () -> Unit) {
  var started by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(
    targetValue = if (started) 1f else 0.88f,
    animationSpec = tween(240, easing = FastOutSlowInEasing),
    label = "splash_scale"
  )
  val alpha by animateFloatAsState(
    targetValue = if (started) 1f else 0f,
    animationSpec = tween(180),
    label = "splash_alpha"
  )

  LaunchedEffect(Unit) {
    started = true
    kotlinx.coroutines.delay(280)
    onFinished()
  }

  Box(
    modifier = Modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    TrawaEmblem(
      size = 82.dp,
      modifier = Modifier.scale(scale).alpha(alpha)
    )
  }
}
