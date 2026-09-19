package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * TRAWA typography contract:
 * - Latin: Outfit
 * - Arabic: Alexandria
 * - Display/brand weight target: 900 (Black)
 * - Body copy stays 400/500 for readability.
 *
 * The supplied project currently contains static font binaries. The reference
 * image specifies Outfit Black and Alexandria Black; those exact Black binary
 * files must be present before the font itself can be marked VERIFIED.
 */
val AlexandriaFontFamily = FontFamily(
  Font(R.font.alexandria, FontWeight.Normal),
  Font(R.font.alexandria, FontWeight.Bold),
  Font(R.font.alexandria, FontWeight.Black)
)

val OutfitFontFamily = FontFamily(
  Font(R.font.outfit, FontWeight.Normal),
  Font(R.font.outfit, FontWeight.Bold),
  Font(R.font.outfit, FontWeight.Black)
)

fun selectFontForText(text: String): FontFamily =
  if (text.any { it in '\u0600'..'\u06FF' }) AlexandriaFontFamily else OutfitFontFamily

/**
 * Compose cannot switch FontFamily per glyph automatically, so the primary
 * family keeps both scripts available. Individual message rendering can use
 * selectFontForText() when it needs an exact script family.
 */
val TrawaPrimaryFontFamily = FontFamily(
  Font(R.font.outfit, FontWeight.Normal),
  Font(R.font.outfit, FontWeight.Bold),
  Font(R.font.outfit, FontWeight.Black),
  Font(R.font.alexandria, FontWeight.Normal),
  Font(R.font.alexandria, FontWeight.Bold),
  Font(R.font.alexandria, FontWeight.Black)
)

private fun trawaStyle(
  weight: FontWeight,
  size: androidx.compose.ui.unit.TextUnit,
  lineHeight: androidx.compose.ui.unit.TextUnit,
  letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) = TextStyle(
  fontFamily = TrawaPrimaryFontFamily,
  fontWeight = weight,
  fontSize = size,
  lineHeight = lineHeight,
  letterSpacing = letterSpacing
)

val Typography = Typography(
  displayLarge = trawaStyle(FontWeight.Black, 36.sp, 44.sp, (-0.5).sp),
  displayMedium = trawaStyle(FontWeight.Black, 30.sp, 38.sp, (-0.25).sp),
  displaySmall = trawaStyle(FontWeight.Black, 26.sp, 34.sp),
  headlineLarge = trawaStyle(FontWeight.Black, 24.sp, 32.sp),
  headlineMedium = trawaStyle(FontWeight.Bold, 21.sp, 28.sp, 0.15.sp),
  headlineSmall = trawaStyle(FontWeight.SemiBold, 18.sp, 25.sp, 0.15.sp),
  titleLarge = trawaStyle(FontWeight.Bold, 19.sp, 26.sp, 0.2.sp),
  titleMedium = trawaStyle(FontWeight.Medium, 16.sp, 23.sp, 0.2.sp),
  titleSmall = trawaStyle(FontWeight.Normal, 14.sp, 20.sp, 0.1.sp),
  bodyLarge = trawaStyle(FontWeight.Normal, 15.sp, 23.sp, 0.1.sp),
  bodyMedium = trawaStyle(FontWeight.Normal, 14.sp, 21.sp, 0.1.sp),
  bodySmall = trawaStyle(FontWeight.Normal, 12.sp, 17.sp, 0.1.sp),
  labelLarge = trawaStyle(FontWeight.Medium, 14.sp, 20.sp, 0.2.sp),
  labelMedium = trawaStyle(FontWeight.Medium, 12.sp, 16.sp, 0.2.sp),
  labelSmall = trawaStyle(FontWeight.Normal, 11.sp, 15.sp, 0.2.sp)
)
