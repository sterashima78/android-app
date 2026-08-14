package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KindleStructuredSeriesMetadataTest {
  @Test
  fun `Web Libraryのシリーズ位置をそのまま利用する`() {
    val parsed = scan(
      """
        {
          "format":"kindle-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example Book",
            "authors":["Author"],
            "coverUrl":null,
            "series":{"id":"B000000099","name":"Example Series","position":3}
          }]
        }
      """.trimIndent(),
    )

    assertEquals(3, parsed["B000000001"]?.position)
    assertEquals("B000000099", parsed["B000000001"]?.seriesId)
    assertEquals("Example Series", parsed["B000000001"]?.seriesName)
  }

  @Test
  fun `シリーズ名が欠けてもIDと位置を保持する`() {
    val parsed = scan(
      """
        {
          "format":"kindle-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example Book",
            "authors":[],
            "coverUrl":null,
            "series":{"id":"B000000099","name":null,"position":8}
          }]
        }
      """.trimIndent(),
    )

    val metadata = parsed.getValue("B000000001")
    assertNull(metadata.seriesName)
    assertEquals("B000000099", metadata.seriesId)
    assertEquals(8, metadata.position)
    assertEquals("シリーズ名未取得 (B000000099)", metadata.toLibrarySeries().name)
  }

  @Test
  fun `手動設定とシリーズ解除をKindleメタデータより優先する`() {
    val metadata = mapOf(
      "B000000001" to KindleSeriesMetadata(
        seriesId = "B000000099",
        seriesName = "Imported Series",
        position = 3,
      ),
    )
    val manual = book("B000000001", series = LibrarySeries("Manual Series", 8))
    val excluded = book("B000000001", excluded = true)
    val automatic = book("b000000001")

    assertEquals("Manual Series", listOf(manual).applyKindleSeries(metadata)[0].series?.name)
    assertNull(listOf(excluded).applyKindleSeries(metadata)[0].series)
    assertEquals("Imported Series", listOf(automatic).applyKindleSeries(metadata)[0].series?.name)
    assertEquals("B000000099", listOf(automatic).applyKindleSeries(metadata)[0].series?.id)
  }

  @Test
  fun `Personal Documentは購入本のシリーズメタデータ更新対象にしない`() {
    val parsed = KindleSeriesMetadataScanner().scan(
      """
        {
          "format":"kindle-personal-library-export",
          "version":1,
          "books":[{
            "id":"0123456789ABCDEF0123456789ABCDEF",
            "title":"Personal Document",
            "authors":[]
          }]
        }
      """.trimIndent(),
    )

    assertNull(parsed)
  }

  private fun scan(json: String): Map<String, KindleSeriesMetadata> =
    requireNotNull(KindleSeriesMetadataScanner().scan(json))

  private fun book(
    sourceId: String,
    series: LibrarySeries? = null,
    excluded: Boolean = false,
  ) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = sourceId,
    title = "Example Book",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
    series = series,
    automaticSeriesExcluded = excluded,
  )
}
