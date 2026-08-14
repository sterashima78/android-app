package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.isKindlePersonalDocument
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KindleWebLibraryImporterTest {
  @Test
  fun `Web Library JSONから蔵書と表紙を取り込む`() {
    val books = parse(
      """
        {
          "format":"kindle-library-export",
          "version":1,
          "exportedAt":"2026-08-14T00:00:00Z",
          "count":2,
          "stats":{"missingCover":1},
          "books":[
            {
              "asin":"B000000001",
              "title":"First Book",
              "authors":["Author A","Author B"],
              "coverUrl":"https://example.invalid/cover.jpg",
              "series":{"id":"B000000099","name":"Example Series","position":1}
            },
            {
              "asin":"B000000002",
              "title":"Second Book",
              "authors":[],
              "coverUrl":null,
              "series":null
            }
          ]
        }
      """.trimIndent(),
    )

    assertEquals(2, books.size)
    assertEquals("https://example.invalid/cover.jpg", books[0].thumbnailUrl)
    assertEquals(listOf("Author A", "Author B"), books[0].authors)
    assertEquals("B000000099", books[0].series?.id)
    assertEquals("Example Series", books[0].series?.name)
    assertEquals(1, books[0].series?.position)
    assertNull(books[1].thumbnailUrl)
    assertNull(books[1].series)
  }

  @Test
  fun `Personal Document JSONから32文字IDと著者を取り込む`() {
    val books = parse(
      """
        {
          "format":"kindle-personal-library-export",
          "version":1,
          "count":2,
          "books":[
            {
              "id":"0123456789ABCDEF0123456789ABCDEF",
              "title":"Personal PDF",
              "authors":["Author A"],
              "contentType":"application/pdf",
              "acquiredAt":1700000000000
            },
            {
              "id":"ABCDEF0123456789ABCDEF0123456789",
              "title":"Personal Book",
              "authors":[],
              "contentType":"application/x-mobipocket-ebook",
              "acquiredAt":1700000000001
            }
          ]
        }
      """.trimIndent(),
    )

    assertEquals(2, books.size)
    assertEquals("PDOC:0123456789ABCDEF0123456789ABCDEF", books[0].sourceId)
    assertEquals(listOf("Author A"), books[0].authors)
    assertTrue(books.all { it.isKindlePersonalDocument() })
    assertNull(books[0].thumbnailUrl)
  }

  @Test
  fun `同じASINが複数あれば最後の書籍を採用する`() {
    val books = parse(
      """
        {
          "format":"kindle-library-export",
          "version":1,
          "books":[
            {"asin":"B000000001","title":"Old","authors":[],"coverUrl":null,"series":null},
            {"asin":"B000000001","title":"New","authors":[],"coverUrl":null,"series":null}
          ]
        }
      """.trimIndent(),
    )

    assertEquals(1, books.size)
    assertEquals("New", books.single().title)
  }

  @Test
  fun `別形式のJSONを拒否する`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parse("""{"format":"other","version":1,"books":[]}""")
    }

    assertEquals("Kindle のエクスポート JSON ではありません", error.message)
  }

  @Test
  fun `Personal Documentの不正なIDを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parse(
        """
          {
            "format":"kindle-personal-library-export",
            "version":1,
            "books":[{"id":"B000000001","title":"Invalid","authors":[]}]
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
            "format":"kindle-library-export",
            "version":1,
            "books":[{
              "asin":"B000000001",
              "title":"Example",
              "authors":[],
              "coverUrl":null,
              "series":{"id":"B000000099","name":"Series","position":0}
            }]
          }
        """.trimIndent(),
      )
    }
  }

  private fun parse(json: String) = KindleWebLibraryImporter().parse(
    fileName = "kindle-library-export.json",
    input = ByteArrayInputStream(json.toByteArray()),
  )
}
