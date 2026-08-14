package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KindleCoverDiagnosticsTest {
  @Test
  fun `版表記だけを検索タイトル末尾から除外する`() {
    assertEquals("Synthetic Book", searchableBookTitle("Synthetic Book (Japanese Edition)"))
    assertEquals("Synthetic Book", searchableBookTitle("Synthetic Book（Kindle Edition）"))
    assertEquals("Synthetic Book", searchableBookTitle("Synthetic Book (English Edition)"))
    assertEquals("Synthetic Book (Revised)", searchableBookTitle("Synthetic Book (Revised)"))
  }

  @Test
  fun `Open Library照合でも版表記を無視する`() {
    val result = selectTitleCandidate(
      book = book("Synthetic Book (Japanese Edition)"),
      candidates = listOf(
        OpenLibraryCandidate(
          key = "/works/OLSYNTHETICW",
          title = "Synthetic Book",
          authors = listOf("Example Author"),
          isbns = emptyList(),
          coverId = 123,
        ),
      ),
    )

    assertEquals(CoverLookupStatus.FOUND, result.status)
  }

  @Test
  fun `Amazon JSON-LDから信頼済み表紙URLを取得する`() {
    val html = """
      <script type="application/ld+json">
        {"@type":"Book","image":"https://m.media-amazon.com/images/I/synthetic-jsonld.jpg"}
      </script>
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/synthetic-jsonld.jpg",
      extractAmazonJsonLdCoverUrl(html),
    )
  }

  @Test
  fun `Amazon image_srcから信頼済み表紙URLを取得する`() {
    val html = """
      <link rel="image_src" href="https://m.media-amazon.com/images/I/synthetic-link.jpg">
    """.trimIndent()

    assertEquals(
      "https://m.media-amazon.com/images/I/synthetic-link.jpg",
      extractAmazonImageSrcCoverUrl(html),
    )
  }

  @Test
  fun `構造化診断には取得経路と候補数だけを保存する`() {
    val trace = listOf(
      CoverLookupTraceStep(
        provider = "GOOGLE_BOOKS",
        status = CoverLookupStatus.NOT_FOUND,
        reason = "AUTHOR_MISMATCH",
        httpStatus = 200,
        candidateCount = 10,
        titleMatchCount = 1,
        authorMatchCount = 0,
      ),
    ).toDiagnosticTrace()

    assertTrue(trace.contains("GOOGLE_BOOKS"))
    assertTrue(trace.contains("AUTHOR_MISMATCH"))
    assertTrue(trace.contains("\"candidateCount\":10"))
    assertFalse(trace.contains("Authorization"))
  }

  private fun book(title: String) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "B0TEST0001",
    title = title,
    authors = listOf("Example Author"),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )
}
