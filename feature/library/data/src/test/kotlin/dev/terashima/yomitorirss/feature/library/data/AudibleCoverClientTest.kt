package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudibleCoverClientTest {
  @Test
  fun `og image から Audible 表紙 URL を取得する`() {
    val html = """
      <html><head>
        <meta property="og:image" content="https://m.media-amazon.com/images/I/cover.jpg">
      </head></html>
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/cover.jpg",
      extractAudibleCoverUrl(html),
    )
  }

  @Test
  fun `meta 属性の順序や引用符に依存しない`() {
    val html = """
      <meta content='https://m.media-amazon.com/images/I/cover.jpg?x=1&amp;y=2' name='twitter:image'>
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/cover.jpg?x=1&y=2",
      extractAudibleCoverUrl(html),
    )
  }

  @Test
  fun `信頼していないホストの画像は採用しない`() {
    val html = """
      <meta property="og:image" content="https://example.com/cover.jpg">
    """.trimIndent()

    assertNull(extractAudibleCoverUrl(html))
  }

  @Test
  fun `http の画像は採用しない`() {
    val html = """
      <meta property="og:image" content="http://m.media-amazon.com/images/I/cover.jpg">
    """.trimIndent()

    assertNull(extractAudibleCoverUrl(html))
  }
}
