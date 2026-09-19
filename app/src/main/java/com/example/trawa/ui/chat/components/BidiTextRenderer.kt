package com.example.trawa.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * 3AR V1 Pro Native Bidirectional Text & Markdown Engine
 *
 * Implements strict Unicode Bidirectional Text (UAX #9) rules:
 * - Intelligent paragraph-level direction detection (First Strong Directional Rule P2)
 * - Directional isolates (LRI \u2066 and PDI \u2069) for embedded Latin/URLs/Emails/Code in Arabic
 * - Directional isolates (RLI \u2067 and PDI \u2069) for embedded Arabic in Latin
 * - Strict LTR enforcement for Code Blocks and URLs
 * - Rich Markdown: Headings, Bold, Italic, Inline Code, Code Blocks, Bullet/Numbered Lists, Blockquotes
 */

// Unicode BiDi Control Characters
private const val LRI = "\u2066" // Left-to-Right Isolate
private const val RLI = "\u2067" // Right-to-Left Isolate
private const val PDI = "\u2069" // Pop Directional Isolate

sealed interface MarkdownBlock {
  data class Paragraph(val text: String) : MarkdownBlock
  data class Heading(val level: Int, val text: String) : MarkdownBlock
  data class BulletItem(val text: String) : MarkdownBlock
  data class NumberedItem(val number: String, val text: String) : MarkdownBlock
  data class Blockquote(val text: String) : MarkdownBlock
  data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

/**
 * Detects if a character is strongly RTL (Arabic, Hebrew, Syriac, Thaana, etc.)
 */
fun isStrongRtlChar(c: Char): Boolean {
  return c in '\u0600'..'\u06FF' || // Arabic
      c in '\u0750'..'\u077F' ||    // Arabic Supplement
      c in '\u08A0'..'\u08FF' ||    // Arabic Extended-A
      c in '\uFB50'..'\uFDFF' ||    // Arabic Presentation Forms-A
      c in '\uFE70'..'\uFEFF' ||    // Arabic Presentation Forms-B
      c in '\u0590'..'\u05FF'       // Hebrew
}

/**
 * Detects if a character is strongly LTR (Latin, Cyrillic, Greek, etc.)
 */
fun isStrongLtrChar(c: Char): Boolean {
  return (c in 'A'..'Z') || (c in 'a'..'z') ||
      c in '\u00C0'..'\u024F' ||    // Latin Extended
      c in '\u0370'..'\u03FF' ||    // Greek
      c in '\u0400'..'\u04FF'       // Cyrillic
}

/**
 * Unicode P2 rule: determines base paragraph direction by looking at the first
 * strongly directional character, backed up by overall character distribution.
 */
fun detectParagraphDirection(text: String): LayoutDirection {
  var arabicCount = 0
  var latinCount = 0
  var firstStrong: LayoutDirection? = null

  for (ch in text) {
    if (isStrongRtlChar(ch)) {
      arabicCount++
      if (firstStrong == null) firstStrong = LayoutDirection.Rtl
    } else if (isStrongLtrChar(ch)) {
      latinCount++
      if (firstStrong == null) firstStrong = LayoutDirection.Ltr
    }
  }

  // Pure or overwhelmingly Latin text (e.g. no Arabic characters)
  if (arabicCount == 0) return LayoutDirection.Ltr

  // English paragraph that merely mentions a single Arabic quote
  if (firstStrong == LayoutDirection.Ltr && latinCount > arabicCount * 4) {
    return LayoutDirection.Ltr
  }

  // If there are substantial Arabic words (3+ characters), it is an Arabic paragraph
  if (arabicCount >= 3) {
    return LayoutDirection.Rtl
  }

  return if (firstStrong == LayoutDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/**
 * Parses raw text containing Markdown into structured blocks.
 */
fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
  val blocks = mutableListOf<MarkdownBlock>()
  val lines = rawText.lines()
  var i = 0

  while (i < lines.size) {
    val line = lines[i]

    // Code block check
    if (line.trimStart().startsWith("```")) {
      val lang = line.trimStart().removePrefix("```").trim()
      val codeLines = mutableListOf<String>()
      i++
      while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
        codeLines.add(lines[i])
        i++
      }
      blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
      i++
      continue
    }

    // Empty lines
    if (line.isBlank()) {
      i++
      continue
    }

    val trimmed = line.trim()

    // Headings
    if (trimmed.startsWith("#")) {
      var level = 0
      while (level < trimmed.length && trimmed[level] == '#' && level < 6) {
        level++
      }
      val headingText = trimmed.substring(level).trim()
      blocks.add(MarkdownBlock.Heading(level, headingText))
      i++
      continue
    }

    // Blockquote
    if (trimmed.startsWith(">")) {
      val quoteText = trimmed.removePrefix(">").trim()
      blocks.add(MarkdownBlock.Blockquote(quoteText))
      i++
      continue
    }

    // Bullet List Item (- , * , • )
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
      val itemText = trimmed.substring(2).trim()
      blocks.add(MarkdownBlock.BulletItem(itemText))
      i++
      continue
    }

    // Numbered List Item (1. , 2. )
    val numberedMatch = Regex("""^(\d+)[\.\)]\s+(.*)$""").find(trimmed)
    if (numberedMatch != null) {
      val num = numberedMatch.groupValues[1]
      val itemText = numberedMatch.groupValues[2]
      blocks.add(MarkdownBlock.NumberedItem(num, itemText))
      i++
      continue
    }

    // Normal Paragraph (combine consecutive non-special lines)
    val paraLines = mutableListOf<String>()
    while (i < lines.size &&
      lines[i].isNotBlank() &&
      !lines[i].trimStart().startsWith("```") &&
      !lines[i].trim().startsWith("#") &&
      !lines[i].trim().startsWith(">") &&
      !lines[i].trim().startsWith("- ") &&
      !lines[i].trim().startsWith("* ") &&
      !lines[i].trim().startsWith("• ") &&
      !Regex("""^(\d+)[\.\)]\s+""").containsMatchIn(lines[i].trim())
    ) {
      paraLines.add(lines[i])
      i++
    }
    blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
  }

  return blocks
}

/**
 * Builds a BiDi-aware AnnotatedString from inline text with support for:
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Inline code (`code`) with LTR isolation
 * - URLs / Links with LTR isolation
 * - Embedded LTR spans inside RTL paragraphs
 */
fun buildBidiAnnotatedString(
  text: String,
  paragraphDirection: LayoutDirection,
  primaryTextColor: Color,
  accentColor: Color
): AnnotatedString {
  val isRtlParagraph = paragraphDirection == LayoutDirection.Rtl

  // Tokenize and process inline markdown
  val builder = AnnotatedString.Builder()
  var cursor = 0

  // Regex for inline patterns: inline code, bold, italic, markdown links, bare URLs, emails
  val pattern = Regex(
    """(`(?<inlinecode>[^`]+)`)""" +
        """|(\*\*(?<bold>[^\*]+)\*\*)""" +
        """|(\*(?<italic>[^\*]+)\*)""" +
        """|(\[(?<linktext>[^\]]+)\]\((?<linkurl>[^\)]+)\))""" +
        """|(?<url>https?://[^\s<>"'{}|\\^`]+)""" +
        """|(?<email>[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})"""
  )

  val matches = pattern.findAll(text).toList()

  for (match in matches) {
    // Append preceding raw text with directional isolate if needed
    if (match.range.first > cursor) {
      val plainSegment = text.substring(cursor, match.range.first)
      appendPlainSegment(builder, plainSegment, isRtlParagraph)
    }

    val groups = match.groups
    val inlineCode = groups["inlinecode"]?.value
    val bold = groups["bold"]?.value
    val italic = groups["italic"]?.value
    val linkText = groups["linktext"]?.value
    val linkUrl = groups["linkurl"]?.value
    val bareUrl = groups["url"]?.value
    val email = groups["email"]?.value

    when {
      inlineCode != null -> {
        // Code is ALWAYS isolated LTR
        builder.append(LRI)
        val start = builder.length
        builder.append(inlineCode)
        builder.addStyle(
          SpanStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            background = Color.White.copy(alpha = 0.08f),
            fontSize = 13.sp
          ),
          start,
          builder.length
        )
        builder.append(PDI)
      }
      bold != null -> {
        val start = builder.length
        appendPlainSegment(builder, bold, isRtlParagraph)
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Black), start, builder.length)
      }
      italic != null -> {
        val start = builder.length
        appendPlainSegment(builder, italic, isRtlParagraph)
        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
      }
      linkText != null && linkUrl != null -> {
        builder.append(LRI)
        val start = builder.length
        builder.append(linkText)
        builder.addStyle(
          SpanStyle(
            color = accentColor,
            fontWeight = FontWeight.Bold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
          ),
          start,
          builder.length
        )
        builder.append(PDI)
      }
      bareUrl != null -> {
        builder.append(LRI)
        val start = builder.length
        builder.append(bareUrl)
        builder.addStyle(
          SpanStyle(
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
          ),
          start,
          builder.length
        )
        builder.append(PDI)
      }
      email != null -> {
        builder.append(LRI)
        val start = builder.length
        builder.append(email)
        builder.addStyle(
          SpanStyle(
            color = accentColor,
            fontWeight = FontWeight.SemiBold
          ),
          start,
          builder.length
        )
        builder.append(PDI)
      }
    }

    cursor = match.range.last + 1
  }

  // Append remaining text
  if (cursor < text.length) {
    appendPlainSegment(builder, text.substring(cursor), isRtlParagraph)
  }

  return builder.toAnnotatedString()
}

/**
 * Handles plain segments by isolating pure Latin / numbers sequences when inside an RTL paragraph
 * so that punctuation (such as dots, slashes, dashes, colons) do not flip or displace.
 */
private fun appendPlainSegment(
  builder: AnnotatedString.Builder,
  segment: String,
  isRtlParagraph: Boolean
) {
  if (!isRtlParagraph) {
    // In LTR paragraph, standard rendering
    builder.append(segment)
    return
  }

  // Inside RTL paragraph: Isolate English / Technical runs that contain embedded punctuation/symbols
  // e.g., "Snapdragon 8 Elite", "Gemini 3.1 Flash", "Android 15", "1,500", "15%", "USD", "EGP", "HTTP 200"
  val latinRunRegex = Regex("""([A-Za-z0-9$€£#][A-Za-z0-9\.\-_,\/:+%#$€£()]*(\s+[A-Za-z0-9\.\-_,\/:+%#$€£()]+)*|[A-Za-z0-9$€£#]+)""")
  var pos = 0
  val matches = latinRunRegex.findAll(segment).toList()

  for (m in matches) {
    if (m.range.first > pos) {
      builder.append(segment.substring(pos, m.range.first))
    }
    // Isolate LTR text run
    builder.append(LRI)
    builder.append(m.value)
    builder.append(PDI)
    pos = m.range.last + 1
  }

  if (pos < segment.length) {
    builder.append(segment.substring(pos))
  }
}

/**
 * Native Composable that renders full BiDi-aware Markdown content.
 */
@Composable
fun BidiFormattedContent(
  content: String,
  modifier: Modifier = Modifier,
  primaryTextColor: Color = MaterialTheme.colorScheme.onSurface,
  accentColor: Color = Color.White
) {
  val blocks = remember(content) { parseMarkdownBlocks(content) }
  val context = LocalContext.current

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    blocks.forEach { block ->
      when (block) {
        is MarkdownBlock.Paragraph -> {
          val dir = detectParagraphDirection(block.text)
          CompositionLocalProvider(LocalLayoutDirection provides dir) {
            val annotated = remember(block.text, dir) {
              buildBidiAnnotatedString(block.text, dir, primaryTextColor, accentColor)
            }
            Text(
              text = annotated,
              style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textDirection = if (dir == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                textAlign = TextAlign.Start
              ),
              color = primaryTextColor,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        is MarkdownBlock.Heading -> {
          val dir = detectParagraphDirection(block.text)
          CompositionLocalProvider(LocalLayoutDirection provides dir) {
            val annotated = remember(block.text, dir) {
              buildBidiAnnotatedString(block.text, dir, primaryTextColor, accentColor)
            }
            val textStyle = when (block.level) {
              1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 21.sp)
              2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontSize = 18.sp)
              else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Text(
              text = annotated,
              style = textStyle.copy(
                textDirection = if (dir == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                textAlign = TextAlign.Start
              ),
              color = primaryTextColor,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp)
            )
          }
        }

        is MarkdownBlock.BulletItem -> {
          val dir = detectParagraphDirection(block.text)
          CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
              verticalAlignment = Alignment.Top
            ) {
              Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                color = accentColor,
                modifier = Modifier.padding(
                  start = if (dir == LayoutDirection.Rtl) 0.dp else 4.dp,
                  end = if (dir == LayoutDirection.Rtl) 8.dp else 8.dp
                )
              )
              val annotated = remember(block.text, dir) {
                buildBidiAnnotatedString(block.text, dir, primaryTextColor, accentColor)
              }
              Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                  fontSize = 15.sp,
                  lineHeight = 22.sp,
                  textDirection = if (dir == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                  textAlign = TextAlign.Start
                ),
                color = primaryTextColor,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        is MarkdownBlock.NumberedItem -> {
          val dir = detectParagraphDirection(block.text)
          CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
              verticalAlignment = Alignment.Top
            ) {
              Text(
                text = "${block.number}.",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
                color = accentColor,
                modifier = Modifier.padding(
                  start = if (dir == LayoutDirection.Rtl) 0.dp else 4.dp,
                  end = if (dir == LayoutDirection.Rtl) 8.dp else 8.dp
                )
              )
              val annotated = remember(block.text, dir) {
                buildBidiAnnotatedString(block.text, dir, primaryTextColor, accentColor)
              }
              Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge.copy(
                  fontSize = 15.sp,
                  lineHeight = 22.sp,
                  textDirection = if (dir == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                  textAlign = TextAlign.Start
                ),
                color = primaryTextColor,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        is MarkdownBlock.Blockquote -> {
          val dir = detectParagraphDirection(block.text)
          CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                val annotated = remember(block.text, dir) {
                  buildBidiAnnotatedString(block.text, dir, primaryTextColor, accentColor)
                }
                Text(
                  text = annotated,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    textDirection = if (dir == LayoutDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
                    textAlign = TextAlign.Start
                  ),
                  color = primaryTextColor.copy(alpha = 0.9f)
                )
              }
            }
          }
        }

        is MarkdownBlock.CodeBlock -> {
          // CODE BLOCKS ARE ALWAYS STRICTLY LTR
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFF030D1E),
              border = BorderStroke(1.dp, TrawaDarkBorder),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = block.language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = Color.White
                  )
                  Row(
                    modifier = Modifier
                      .clickable {
                        val clip = ClipData.newPlainText("Code", block.code)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                          .setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                      }
                      .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.ContentCopy,
                      contentDescription = "Copy code",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "Copy",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = block.code,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 13.sp,
                  lineHeight = 19.sp,
                  color = Color(0xFFF1F5F9),
                  style = TextStyle(
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.Start
                  )
                )
              }
            }
          }
        }
      }
    }
  }
}
