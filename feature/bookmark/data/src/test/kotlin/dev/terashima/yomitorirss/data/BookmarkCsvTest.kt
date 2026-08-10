package dev.terashima.yomitorirss.feature.bookmark.data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class BookmarkCsvTest {
  @Test
  fun `添付CSV形式をブックマークへ変換できる`() {
    val csv = "\uFEFF" + """id,title,note,excerpt,url,folder,tags,created,cover,highlights,favorite
1,記事タイトル,,,https://example.com/article,開発,"llm, chatgpt",2024-11-17T21:49:16.863Z,,,false
"""

    val result = parseBookmarkCsv(StringReader(csv), "2026-08-06T00:00:00Z")

    assertEquals(1, result.entries.size)
    assertEquals(0, result.skippedRows)
    assertEquals("記事タイトル", result.entries.single().title)
    assertEquals("example.com", result.entries.single().sourceTitle)
    assertEquals(listOf("開発", "llm", "chatgpt"), result.entries.single().tagNames)
    assertEquals("2024-11-17T21:49:16.863Z", result.entries.single().createdAt)
  }

  @Test
  fun `引用符内の改行と二重引用符を処理できる`() {
    val csv = "title,url,created\r\n\"複数行\n\"\"記事\"\"\",https://example.com,2026-08-01T00:00:00Z\r\n"

    val entry = parseBookmarkCsv(StringReader(csv)).entries.single()

    assertEquals("複数行\n\"記事\"", entry.title)
  }

  @Test
  fun `Unsortedはタグにせず不正URLをスキップする`() {
    val csv = """title,url,folder,tags
正しい記事,https://example.com,Unsorted,"css, CSS"
不正な記事,not-a-url,開発,kotlin
"""

    val result = parseBookmarkCsv(StringReader(csv), "2026-08-06T00:00:00Z")

    assertEquals(1, result.entries.size)
    assertEquals(listOf("css"), result.entries.single().tagNames)
    assertEquals(1, result.skippedRows)
  }

  @Test
  fun `url列がなければエラーにする`() {
    val error = runCatching {
      parseBookmarkCsv(StringReader("title,created\n記事,2026-08-01T00:00:00Z"))
    }.exceptionOrNull()

    assertTrue(error?.message?.contains("url列") == true)
  }
}
