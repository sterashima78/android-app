package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KindleStructuredSeriesMetadataTest {
  @Test
  fun `シリーズ位置を1始まりへ変換する`() {
    val csv = """
      series-ASIN,series-product-name,item-ASIN,item-position-in-series
      SERIES_A,Example Series,ITEM_A,0
      SERIES_A,Example Series,ITEM_B,1
    """.trimIndent()

    val parsed = KindleSeriesMetadataParser.parse(csv.toByteArray())

    assertEquals(1, parsed["ITEM_A"]?.series?.position)
    assertEquals(2, parsed["ITEM_B"]?.series?.position)
  }

  @Test
  fun `入れ子ZIP内のシリーズCSVを読み込む`() {
    val csv = """
      series-ASIN,series-product-name,item-ASIN,item-position-in-series
      SERIES_A,Example Series,ITEM_A,0
    """.trimIndent().toByteArray()
    val nested = zipOf("export/Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv" to csv)
    val outer = zipOf("archive/export.zip" to nested)

    val parsed = KindleSeriesMetadataScanner().scan(
      fileName = "amazon-export.zip",
      input = ByteArrayInputStream(outer),
    )

    assertEquals("Example Series", parsed["ITEM_A"]?.series?.name)
  }

  @Test
  fun `手動設定とシリーズ解除をKindleメタデータより優先する`() {
    val metadata = mapOf(
      "ITEM_A" to KindleSeriesMetadata(
        seriesId = "SERIES_A",
        series = LibrarySeries("Imported Series", 3),
      ),
    )
    val manual = book("ITEM_A", series = LibrarySeries("Manual Series", 8))
    val excluded = book("ITEM_A", excluded = true)
    val automatic = book("item_a")

    assertEquals("Manual Series", listOf(manual).applyKindleSeries(metadata)[0].series?.name)
    assertNull(listOf(excluded).applyKindleSeries(metadata)[0].series)
    assertEquals("Imported Series", listOf(automatic).applyKindleSeries(metadata)[0].series?.name)
  }

  private fun zipOf(vararg files: Pair<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
      files.forEach { (name, bytes) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return output.toByteArray()
  }

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
