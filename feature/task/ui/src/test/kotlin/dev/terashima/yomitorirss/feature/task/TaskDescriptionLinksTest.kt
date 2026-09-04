package dev.terashima.yomitorirss.feature.task

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskDescriptionLinksTest {
  @Test
  fun `HTTPとHTTPSのURLを説明文から検出する`() {
    val description = "参照 https://example.com/docs と http://example.org/path?q=1"

    val urls = findTaskDescriptionUrls(description)

    assertEquals(
      listOf("https://example.com/docs", "http://example.org/path?q=1"),
      urls.map { it.value },
    )
    assertEquals(
      urls.map { description.substring(it.range) },
      urls.map { it.value },
    )
  }

  @Test
  fun `URL末尾の文章用記号はリンクに含めない`() {
    val description = "詳細は https://example.com/docs。次は https://example.org/path), を参照"

    val urls = findTaskDescriptionUrls(description)

    assertEquals(
      listOf("https://example.com/docs", "https://example.org/path"),
      urls.map { it.value },
    )
  }

  @Test
  fun `URLのschemeは大文字小文字を区別しない`() {
    val urls = findTaskDescriptionUrls("HTTPS://example.com/path")

    assertEquals(listOf("HTTPS://example.com/path"), urls.map { it.value })
  }

  @Test
  fun `説明文のURLにはLinkAnnotationを付与する`() {
    val description = "参照 https://example.com/docs。"

    val annotated = taskDescriptionAnnotatedString(description, Color.Blue)
    val links = annotated.getLinkAnnotations(0, annotated.length)

    assertEquals(description, annotated.text)
    assertEquals(1, links.size)
    val link = links.single().item as LinkAnnotation.Url
    assertEquals("https://example.com/docs", link.url)
    assertEquals(description.indexOf("https://"), links.single().start)
    assertEquals(description.indexOf("。"), links.single().end)
  }

  @Test
  fun `URLがない説明文ではリンクを作らない`() {
    assertTrue(findTaskDescriptionUrls("通常の説明文です").isEmpty())
  }
}
