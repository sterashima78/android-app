package dev.terashima.yomitorirss.feature.mail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailHtmlDocumentTest {
  @Test
  fun `HTML断片は表示用文書でラップする`() {
    val document = htmlDocument(
      html = "<div>本文</div>",
      textColor = Color.Black,
      linkColor = Color.Blue,
    )

    assertTrue(document.contains("<body><div>本文</div></body>"))
    assertTrue(document.contains("name=\"viewport\""))
    assertEquals(1, tagCount(document, "html"))
    assertEquals(1, tagCount(document, "body"))
  }

  @Test
  fun `完全なHTML文書は入れ子にせず既存headへ表示設定を追加する`() {
    val source = """
      <!doctype html>
      <html>
        <head><style>.message { width: 600px; }</style></head>
        <body><div class="message">本文</div></body>
      </html>
    """.trimIndent()

    val document = htmlDocument(
      html = source,
      textColor = Color.Black,
      linkColor = Color.Blue,
    )

    assertTrue(document.contains(".message { width: 600px; }"))
    assertTrue(document.contains("name=\"viewport\""))
    assertEquals(1, tagCount(document, "html"))
    assertEquals(1, tagCount(document, "body"))
  }

  @Test
  fun `headのないHTML文書にはheadを追加する`() {
    val document = htmlDocument(
      html = "<html><body>本文</body></html>",
      textColor = Color.Black,
      linkColor = Color.Blue,
    )

    assertTrue(document.contains("<html><head>"))
    assertEquals(1, tagCount(document, "head"))
    assertEquals(1, tagCount(document, "body"))
  }

  @Test
  fun `既存viewportは重複して追加しない`() {
    val source = """
      <html>
        <head><meta name="viewport" content="width=640"></head>
        <body>本文</body>
      </html>
    """.trimIndent()

    val document = htmlDocument(
      html = source,
      textColor = Color.Black,
      linkColor = Color.Blue,
    )

    assertEquals(1, Regex("name\\s*=\\s*['\"]?viewport", RegexOption.IGNORE_CASE).findAll(document).count())
  }

  private fun tagCount(document: String, tag: String): Int =
    Regex("<$tag\\b", RegexOption.IGNORE_CASE).findAll(document).count()
}
