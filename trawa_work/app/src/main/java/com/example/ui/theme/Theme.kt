package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = Color.White,
  onPrimary = Color.Black,
  primaryContainer = TrawaDarkComposer,
  onPrimaryContainer = Color.White,
  secondary = Color.White,
  onSecondary = Color.Black,
  secondaryContainer = TrawaDarkSurface,
  onSecondaryContainer = TrawaTextSecondaryDark,
  tertiary = Color(0xFFE4E4E7),
  onTertiary = Color.Black,
  background = TrawaDarkBackground,
  onBackground = TrawaTextPrimaryDark,
  surface = TrawaDarkSurface,
  onSurface = TrawaTextPrimaryDark,
  surfaceVariant = TrawaDarkSurfaceVariant,
  onSurfaceVariant = TrawaTextSecondaryDark,
  outline = TrawaDarkBorder,
  error = TrawaError,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = Color.Black,
  onPrimary = Color.White,
  primaryContainer = TrawaLightComposer,
  onPrimaryContainer = Color.Black,
  secondary = Color.Black,
  onSecondary = Color.White,
  secondaryContainer = TrawaLightSurfaceVariant,
  onSecondaryContainer = TrawaTextSecondaryLight,
  tertiary = Color(0xFF52525B),
  onTertiary = Color.White,
  background = TrawaLightBackground,
  onBackground = TrawaTextPrimaryLight,
  surface = TrawaLightSurface,
  onSurface = TrawaTextPrimaryLight,
  surfaceVariant = TrawaLightSurfaceVariant,
  onSurfaceVariant = TrawaTextSecondaryLight,
  outline = TrawaLightBorder,
  error = TrawaError,
  onError = Color.White
)

@Composable
fun TrawaTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
