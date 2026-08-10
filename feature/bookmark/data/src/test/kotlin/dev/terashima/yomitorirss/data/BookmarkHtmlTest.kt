package dev.terashima.yomitorirss.feature.bookmark.data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class BookmarkHtmlTest {
  @Test
  fun `Netscape形式のブックマークとフォルダ階層を変換できる`() {
    val html = """
      <!DOCTYPE NETSCAPE-Bookmark-file-1>
      <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
      <TITLE>Bookmarks</TITLE>
      <H1>Bookmarks</H1>
      <DL><p>
        <DT><A HREF="https://root.example/article" ADD_DATE="1725148800">Root &amp; Article</A>
        <DT><H3>技術</H3>
        <DL><p>
          <DT><A HREF="https://www.example.com/article?x=1&amp;y=2">子記事</A>
          <DT><H3>Android</H3>
          <DL><p>
            <DT><A HREF="https://developer.android.com/guide">Androidガイド</A>
          </DL><p>
        </DL><p>
      </DL><p>
    """.trimIndent()

    val result = parseBookmarkHtml(StringReader(html), "2026-08-06T00:00:00Z")

    assertEquals(3, result.entries.size)
    assertEquals(0, result.skippedEntries)

    val root = result.entries[0]
    assertEquals("Root & Article", root.title)
    assertEquals("2024-09-01T00:00:00Z", root.createdAt)
    assertEquals(emptyList<String>(), root.tagNames)

    val child = result.entries[1]
    assertEquals("https://www.example.com/article?x=1&y=2", child.url)
    assertEquals("example.com", child.sourceTitle)
    assertEquals(listOf("技術"), child.tagNames)

    val grandchild = result.entries[2]
    assertEquals(listOf("技術", "Android"), grandchild.tagNames)
  }

  @Test
  fun `不正URLをスキップし空タイトルはホスト名で補完する`() {
    val html = """
      <!DOCTYPE NETSCAPE-Bookmark-file-1>
      <DL><p>
        <DT><A HREF="javascript:alert(1)">不正</A>
        <DT><A HREF="https://www.example.org/path"></A>
      </DL><p>
    """.trimIndent()

    val result = parseBookmarkHtml(StringReader(html), "2026-08-06T00:00:00Z")

    assertEquals(1, result.entries.size)
    assertEquals(1, result.skippedEntries)
    assertEquals("example.org", result.entries.single().title)
    assertEquals("2026-08-06T00:00:00Z", result.entries.single().createdAt)
  }

  @Test
  fun `通常のHTMLはブックマーク形式として扱わない`() {
    val error = runCatching {
      parseBookmarkHtml(StringReader("<html><body><a href=\"https://example.com\">記事</a></body></html>"))
    }.exceptionOrNull()

    assertTrue(error?.message?.contains("ブックマークHTML形式") == true)
  }
}
