package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AudibleStructuredSeriesMetadataTest {
  @Test
  fun `Web JSONからシリーズ名と位置を読み込む`() {
    val parsed = AudibleSeriesMetadataScanner().scan(
      """
        {
          "format":"audible-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example",
            "authors":[],
            "series":{"id":"B000000099","name":"Example Series","position":4}
          }]
        }
      """.trimIndent(),
    )

    val metadata = parsed.getValue("B000000001")
    assertEquals("B000000099", metadata.seriesId)
    assertEquals("Example Series", metadata.seriesName)
    assertEquals(4, metadata.position)
  }

  @Test
  fun `WebView形式ではないJSONを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      AudibleSeriesMetadataScanner().scan(
        """{"format":"other","version":1,"books":[]}""",
      )
    }
  }

  @Test
  fun `手動設定とシリーズ解除をAudibleメタデータより優先する`() {
    val metadata = mapOf(
      "B000000001" to AudibleSeriesMetadata(
        seriesId = "B000000099",
        seriesName = "Imported Series",
        position = 3,
      ),
    )
    val manual = book(series = LibrarySeries("Manual Series", 8))
    val excluded = book(excluded = true)
    val automatic = book()

    assertEquals("Manual Series", listOf(manual).applyAudibleSeries(metadata)[0].series?.name)
    assertNull(listOf(excluded).applyAudibleSeries(metadata)[0].series)
    assertEquals("Imported Series", listOf(automatic).applyAudibleSeries(metadata)[0].series?.name)
    assertEquals("B000000099", listOf(automatic).applyAudibleSeries(metadata)[0].series?.id)
  }

  private fun book(
    series: LibrarySeries? = null,
    excluded: Boolean = false,
  ) = LibraryBook(
    source = LibrarySource.AUDIBLE,
    sourceId = "B000000001",
    title = "Example",
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
