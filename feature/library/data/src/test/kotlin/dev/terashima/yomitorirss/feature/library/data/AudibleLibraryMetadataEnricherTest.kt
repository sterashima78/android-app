package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AudibleLibraryMetadataEnricherTest {
  private val enricher = AudibleLibraryMetadataEnricher()

  @Test
  fun `Audible Library csv からナレーターと再生時間と商品 URL を補完する`() {
    val csv =
      "Title,Author,Narrator,ASIN,Duration,Release Date,Deleted\n" +
        "Audio Book,Writer,Reader One / Reader Two,B0B2YW7Q8J,12:34:56,2026-01-02,false\n"

    val enriched = enricher.enrich(
      fileName = "Library.csv",
      bytes = csv.toByteArray(),
      books = listOf(audibleBook(sourceId = "B0B2YW7Q8J")),
    ).single()

    assertEquals(listOf("Reader One", "Reader Two"), enriched.narrators)
    assertEquals("12:34:56", enriched.duration)
    assertEquals("https://www.audible.co.jp/pd/B0B2YW7Q8J", enriched.infoUrl)
  }

  @Test
  fun `Audible ZIP では Library csv のメタデータだけを使う`() {
    val bytes = zipOf(
      "Audible/Library.csv" to
        "Title,Narrated By,Audible ASIN,Listening Length\nAudio Book,Reader,B0B2YW7Q8J,9 hrs\n",
      "Audible/Listening History.csv" to
        "Title,Narrator,ASIN,Duration\nHistory Item,Wrong Reader,B0B2YW7Q8J,99 hrs\n",
    )

    val enriched = enricher.enrich(
      fileName = "audible-export.zip",
      bytes = bytes,
      books = listOf(audibleBook(sourceId = "B0B2YW7Q8J")),
    ).single()

    assertEquals(listOf("Reader"), enriched.narrators)
    assertEquals("9 hrs", enriched.duration)
  }

  @Test
  fun `Audible の明示商品 URL があれば ASIN 生成 URL より優先する`() {
    val csv =
      "Title,ASIN,Product URL\n" +
        "Audio Book,B0B2YW7Q8J,https://www.audible.co.jp/pd/example/B0B2YW7Q8J\n"

    val enriched = enricher.enrich(
      fileName = "Library.csv",
      bytes = csv.toByteArray(),
      books = listOf(audibleBook(sourceId = "B0B2YW7Q8J")),
    ).single()

    assertEquals("https://www.audible.co.jp/pd/example/B0B2YW7Q8J", enriched.infoUrl)
  }

  private fun audibleBook(sourceId: String): LibraryBook = LibraryBook(
    source = LibrarySource.AUDIBLE,
    sourceId = sourceId,
    title = "Audio Book",
    authors = listOf("Writer"),
    publisher = null,
    publishedDate = "2026-01-02",
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private fun zipOf(vararg files: Pair<String, String>): ByteArray =
    ByteArrayOutputStream().use { output ->
      ZipOutputStream(output).use { zip ->
        files.forEach { (name, text) ->
          zip.putNextEntry(ZipEntry(name))
          zip.write(text.toByteArray())
          zip.closeEntry()
        }
      }
      output.toByteArray()
    }
}
