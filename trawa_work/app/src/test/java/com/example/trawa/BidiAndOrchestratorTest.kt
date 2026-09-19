package com.example.trawa

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import com.example.trawa.ui.chat.components.MarkdownBlock
import com.example.trawa.ui.chat.components.buildBidiAnnotatedString
import com.example.trawa.ui.chat.components.detectParagraphDirection
import com.example.trawa.ui.chat.components.parseMarkdownBlocks
import org.junit.Assert.*
import org.junit.Test

class BidiAndOrchestratorTest {

  @Test
  fun `test all required mixed-language bidirectional strings detect paragraph direction accurately`() {
    val rtlExamples = listOf(
      "إيه أحدث إصدار من Android؟",
      "أنا بستخدم Samsung Galaxy S25 Ultra.",
      "اشرحلي يعني إيه API endpoint.",
      "Gemini هو model للـAI، لكن مش لازم يكون هو provider الوحيد.",
      "السعر الحالي حوالي 500 USD.",
      "آخر تحديث نزل يوم 19 September 2026.",
      "افتح https://example.com وشوف التفاصيل.",
      "email@example.com هو البريد المستخدم.",
      "HTTP 200 يعني إن الطلب نجح.",
      "أنا عايز أعرف آخر إصدار من Android وامتى نزل",
      "الـSamsung S25 Ultra بيستخدم Snapdragon 8 Elite",
      "سعره 500 USD في 2026"
    )

    for (str in rtlExamples) {
      val direction = detectParagraphDirection(str)
      assertEquals("String '$str' should detect as RTL", LayoutDirection.Rtl, direction)
    }

    val ltrExamples = listOf(
      "Error 404: Resource not found.",
      "The latest release of Android was launched on 19 September 2026.",
      "Samsung Galaxy S25 Ultra is powered by Snapdragon 8 Elite."
    )

    for (str in ltrExamples) {
      val direction = detectParagraphDirection(str)
      assertEquals("String '$str' should detect as LTR", LayoutDirection.Ltr, direction)
    }
  }

  @Test
  fun `test bidi annotated string builder correctly isolates embedded LTR tokens in Arabic`() {
    val sample = "افتح https://example.com وتواصل عبر support@trawa.ai لمزيد من التفاصيل."
    val annotated = buildBidiAnnotatedString(
      text = sample,
      paragraphDirection = LayoutDirection.Rtl,
      primaryTextColor = Color.White,
      accentColor = Color.Cyan
    )

    // LRI (\u2066) and PDI (\u2069) should isolate the URL and Email
    assertTrue("Should contain Left-to-Right isolate", annotated.text.contains("\u2066"))
    assertTrue("Should contain Pop-Directional isolate", annotated.text.contains("\u2069"))
    assertTrue("Should preserve URL", annotated.text.contains("https://example.com"))
    assertTrue("Should preserve Email", annotated.text.contains("support@trawa.ai"))
  }

  @Test
  fun `test markdown block parser parses headings lists and code blocks`() {
    val markdown = """
      # 3AR V1 Pro Documentation
      
      Here is a code example:
      ```kotlin
      val response = api.chat("Hello")
      ```
      
      Features:
      - Bidirectional text support
      - Multi-provider AI orchestration
      
      Steps:
      1. First step
      2. Second step
      
      > Important quote on security
    """.trimIndent()

    val blocks = parseMarkdownBlocks(markdown)
    assertTrue("Should have multiple blocks", blocks.size >= 6)
    assertTrue("Should have a heading", blocks.any { it is MarkdownBlock.Heading })
    assertTrue("Should have a code block", blocks.any { it is MarkdownBlock.CodeBlock })
    assertTrue("Should have bullet items", blocks.any { it is MarkdownBlock.BulletItem })
    assertTrue("Should have numbered items", blocks.any { it is MarkdownBlock.NumberedItem })
    assertTrue("Should have a blockquote", blocks.any { it is MarkdownBlock.Blockquote })
  }

}
