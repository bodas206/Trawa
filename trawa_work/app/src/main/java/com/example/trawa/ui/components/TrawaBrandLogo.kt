package com.example.trawa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun TrawaEmblem(
  size: Dp = 40.dp,
  modifier: Modifier = Modifier
) {
  androidx.compose.foundation.Image(
    painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.trawa_logo_transparent),
    contentDescription = "TRAWA",
    modifier = modifier.size(size),
    contentScale = androidx.compose.ui.layout.ContentScale.Fit
  )
}

@Composable
fun TrawaHeaderBrand(
  modifier: Modifier = Modifier,
  showSubtitle: Boolean = true
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    TrawaEmblem(size = 32.dp)
    Column {
      Text(
        text = "TRAWA",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.onBackground
      )
      if (showSubtitle) {
        Text(
          text = "3AR V1 Pro",
          style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = TrawaCyan
        )
      }
    }
  }
}
