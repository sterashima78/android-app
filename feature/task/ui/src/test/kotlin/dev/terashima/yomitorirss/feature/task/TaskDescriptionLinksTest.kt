package dev.terashima.yomitorirss.feature.task

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
  fun `URLがない説明文ではリンクを作らない`() {
    assertTrue(findTaskDescriptionUrls("通常の説明文です").isEmpty())
  }
}
