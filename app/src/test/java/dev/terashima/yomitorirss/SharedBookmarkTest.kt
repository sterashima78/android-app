package dev.terashima.yomitorirss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedBookmarkTest {
  @Test
  fun `URLだけの共有ではホスト名をタイトルにする`() {
    val bookmark = parseSharedBookmark("https://example.com/articles/1", null)

    assertEquals("https://example.com/articles/1", bookmark?.url)
    assertEquals("example.com", bookmark?.title)
    assertEquals("example.com", bookmark?.sourceTitle)
  }

  @Test
  fun `共有件名を記事タイトルとして使う`() {
    val bookmark = parseSharedBookmark(
      text = "https://example.com/articles/1",
      subject = "共有された記事",
    )

    assertEquals("共有された記事", bookmark?.title)
  }

  @Test
  fun `本文に含まれるタイトルとURLを分離する`() {
    val bookmark = parseSharedBookmark(
      text = "共有された記事\nhttps://example.com/articles/1",
      subject = null,
    )

    assertEquals("共有された記事", bookmark?.title)
    assertEquals("https://example.com/articles/1", bookmark?.url)
  }

  @Test
  fun `URL末尾の句読点は除外する`() {
    val bookmark = parseSharedBookmark("記事 https://example.com/articles/1）。", null)

    assertEquals("https://example.com/articles/1", bookmark?.url)
  }

  @Test
  fun `HTTP以外の共有テキストはブックマークにしない`() {
    assertNull(parseSharedBookmark("記事本文だけです", "件名"))
  }
}
