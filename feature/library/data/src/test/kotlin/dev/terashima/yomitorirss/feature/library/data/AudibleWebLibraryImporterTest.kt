package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AudibleWebLibraryImporterTest {
  @Test
  fun `v1 JSONからAudible蔵書を取り込む`() {
    val books = parse(
      """
        {
          "format":"audible-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example Audio Book",
            "subtitle":"Example Subtitle",
            "authors":["Author A","Author B"],
            "narrators":["Narrator A"],
            "publisher":"Example Publisher",
            "publishedDate":"2026-08-14",
            "description":"<p>Example<br>description</p>",
            "coverUrl":"https://example.invalid/cover.jpg",
            "durationMinutes":584,
            "series":{"id":"B000000099","name":"Example Series","position":2},
            "productUrl":"intent://untrusted"
          }]
        }
      """.trimIndent(),
    )

    val book = books.single()
    assertEquals("B000000001", book.sourceId)
    assertEquals("Example Audio Book", book.title)
    assertEquals(listOf("Author A", "Author B"), book.authors)
    assertEquals(listOf("Narrator A"), book.narrators)
    assertEquals("Example Publisher", book.publisher)
    assertEquals("2026-08-14", book.publishedDate)
    assertEquals("Example description", book.description)
    assertEquals("https://example.invalid/cover.jpg", book.thumbnailUrl)
    assertEquals("9時間44分", book.duration)
    assertEquals("https://www.audible.co.jp/pd/B000000001", book.infoUrl)
    assertEquals("B000000099", book.series?.id)
    assertEquals("Example Series", book.series?.name)
    assertEquals(2, book.series?.position)
  }

  @Test
  fun `裸配列JSONを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parse(
        """
          [
            {
              "asin":"B000000001",
              "title":"First",
              "authors":["Author"]
            }
          ]
        """.trimIndent(),
      )
    }
  }

  @Test
  fun `同じASINが複数あれば最後の書籍とシリーズを採用する`() {
    val export = parseExport(
      """
        {
          "format":"audible-library-export",
          "version":1,
          "books":[
            {"asin":"B000000001","title":"Old","authors":[],"series":{"name":"Old Series","position":1}},
            {"asin":"B000000001","title":"New","authors":[],"series":{"name":"New Series","position":3}}
          ]
        }
      """.trimIndent(),
    )

    assertEquals(1, export.books.size)
    assertEquals("New", export.books.single().title)
    assertEquals("New Series", export.seriesBySourceId.getValue("B000000001").seriesName)
    assertEquals(3, export.seriesBySourceId.getValue("B000000001").position)
  }

  @Test
  fun `不正なASINを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parse(
        """
          {
            "format":"audible-library-export",
            "version":1,
            "books":[{"asin":"bad","title":"Example","authors":[]}]
          }
        """.trimIndent(),
      )
    }
  }

  @Test
  fun `不正なシリーズ位置を拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parse(
        """
          {
            "format":"audible-library-export",
            "version":1,
            "books":[{
              "asin":"B000000001",
              "title":"Example",
              "authors":[],
              "series":{"name":"Example Series","position":0}
            }]
          }
        """.trimIndent(),
      )
    }
  }

  @Test
  fun `https以外の表紙URLは保存しない`() {
    val books = parse(
      """
        {
          "format":"audible-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example",
            "authors":[],
            "coverUrl":"file:///tmp/cover.jpg"
          }]
        }
      """.trimIndent(),
    )

    assertNull(books.single().thumbnailUrl)
  }

  private fun parse(json: String) = AudibleWebLibraryImporter().parse(json)

  private fun parseExport(json: String) = AudibleWebLibraryExportParser.parse(json)
}
