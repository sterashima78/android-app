package dev.terashima.yomitorirss.entry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedBookmarkTest {
  @Test
  fun `URLだけの共有ではホスト名をタイトルにする`() {
    val bookmark = parseSharedBookmark(
      text = "https://example.com/articles/1",
      contentTitle = null,
      subject = null,
    )

    assertEquals("https://example.com/articles/1", bookmark?.url)
    assertEquals("example.com", bookmark?.title)
    assertEquals("example.com", bookmark?.sourceTitle)
  }

  @Test
  fun `共有コンテンツタイトルを記事タイトルとして使う`() {
    val bookmark = parseSharedBookmark(
      text = "https://example.com/articles/1",
      contentTitle = "共有されたページ",
      subject = null,
    )

    assertEquals("共有されたページ", bookmark?.title)
  }

  @Test
  fun `共有コンテンツタイトルを件名より優先する`() {
    val bookmark = parseSharedBookmark(
      text = "https://example.com/articles/1",
      contentTitle = "共有されたページ",
      subject = "共有された件名",
    )

    assertEquals("共有されたページ", bookmark?.title)
  }

  @Test
  fun `共有件名を記事タイトルとして使う`() {
    val bookmark = parseSharedBookmark(
      text = "https://example.com/articles/1",
      contentTitle = null,
      subject = "共有された記事",
    )

    assertEquals("共有された記事", bookmark?.title)
  }

  @Test
  fun `本文に含まれるタイトルとURLを分離する`() {
    val bookmark = parseSharedBookmark(
      text = "共有された記事\nhttps://example.com/articles/1",
      contentTitle = null,
      subject = null,
    )

    assertEquals("共有された記事", bookmark?.title)
    assertEquals("https://example.com/articles/1", bookmark?.url)
  }

  @Test
  fun `URL末尾の句読点は除外する`() {
    val bookmark = parseSharedBookmark(
      text = "記事 https://example.com/articles/1）。",
      contentTitle = null,
      subject = null,
    )

    assertEquals("https://example.com/articles/1", bookmark?.url)
  }

  @Test
  fun `HTTP以外の共有テキストはブックマークにしない`() {
    assertNull(
      parseSharedBookmark(
        text = "記事本文だけです",
        contentTitle = "共有されたページ",
        subject = "件名",
      ),
    )
  }
}
