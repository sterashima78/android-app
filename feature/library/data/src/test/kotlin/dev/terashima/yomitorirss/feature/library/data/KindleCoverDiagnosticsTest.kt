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
  fun `構造化診断v2には取得経路と書誌識別子だけを保存する`() {
    val trace = listOf(
      CoverLookupTraceStep(
        provider = "GOOGLE_BOOKS",
        status = CoverLookupStatus.NOT_FOUND,
        reason = "MATCHED_BOOK_WITHOUT_COVER",
        operation = "BIBLIOGRAPHIC_SEARCH",
        httpStatus = 200,
        candidateCount = 10,
        titleMatchCount = 1,
        authorMatchCount = 1,
      ),
    ).toDiagnosticTrace(
      resolvedIdentifiers = listOf(
        ResolvedBookIdentifier(
          type = "ISBN_13",
          value = "9781234567897",
          relation = BookIdentifierRelation.SAME_WORK,
          source = "GOOGLE_BOOKS",
        ),
      ),
      nextAttemptAtEpochMillis = 123456789L,
    )

    assertTrue(trace.contains("\"version\":2"))
    assertTrue(trace.contains("GOOGLE_BOOKS"))
    assertTrue(trace.contains("BIBLIOGRAPHIC_SEARCH"))
    assertTrue(trace.contains("9781234567897"))
    assertTrue(trace.contains("SAME_WORK"))
    assertTrue(trace.contains("\"nextAttemptAt\":123456789"))
    assertFalse(trace.contains("Authorization"))
  }

  @Test
  fun `通常の一時エラーは15分2時間24時間で再試行する`() {
    val step = CoverLookupTraceStep(
      provider = "OPEN_LIBRARY",
      status = CoverLookupStatus.ERROR,
      reason = "HTTP_RETRYABLE",
      retryable = true,
      httpStatus = 503,
    )

    assertEquals(15L * 60 * 1000, kindleCoverRetryDelayMillis(1, listOf(step)))
    assertEquals(2L * 60 * 60 * 1000, kindleCoverRetryDelayMillis(2, listOf(step)))
    assertEquals(24L * 60 * 60 * 1000, kindleCoverRetryDelayMillis(3, listOf(step)))
  }

  @Test
  fun `Retry-Afterを通常バックオフより優先する`() {
    val step = CoverLookupTraceStep(
      provider = "GOOGLE_BOOKS",
      status = CoverLookupStatus.ERROR,
      reason = "HTTP_RETRYABLE",
      retryable = true,
      retryAfterSeconds = 600,
      httpStatus = 429,
    )

    assertEquals(600_000L, kindleCoverRetryDelayMillis(1, listOf(step)))
  }

  @Test
  fun `Amazon challengeは24時間待つ`() {
    val step = CoverLookupTraceStep(
      provider = "AMAZON_PRODUCT_PAGE",
      status = CoverLookupStatus.ERROR,
      reason = "CHALLENGE_PAGE",
      retryable = true,
    )

    assertEquals(24L * 60 * 60 * 1000, kindleCoverRetryDelayMillis(1, listOf(step)))
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
