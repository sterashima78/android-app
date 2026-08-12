package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonLibraryImporterTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `Kindle の ownership JSON から現在所有している書籍だけ解析する`() {
    val json = """
      [
        {
          "rightType": "GRANT",
          "eventTimestamp": "2026-01-01T00:00:00Z",
          "content": {
            "asin": "KINDLE1",
            "title": "Owned Book",
            "authors": ["Alice", "Bob"],
            "contentType": "E-Book"
          }
        },
        {
          "rightType": "GRANT",
          "eventTimestamp": "2026-01-01T00:00:00Z",
          "content": {
            "asin": "KINDLE2",
            "title": "Returned Book",
            "contentType": "E-Book"
          }
        },
        {
          "rightType": "REVOKE",
          "eventTimestamp": "2026-02-01T00:00:00Z",
          "content": {
            "asin": "KINDLE2",
            "title": "Returned Book",
            "contentType": "E-Book"
          }
        },
        {
          "rightType": "GRANT",
          "eventTimestamp": "2026-01-01T00:00:00Z",
          "content": {
            "asin": "MUSIC1",
            "title": "Not a Book",
            "contentType": "Music"
          }
        }
      ]
    """.trimIndent()

    val books = importer.parse(
      LibrarySource.KINDLE,
      "Digital.Content.Ownership.1.json",
      json.toByteArray(),
    )

    assertEquals(1, books.size)
    assertEquals("KINDLE1", books.single().sourceId)
    assertEquals("Owned Book", books.single().title)
    assertEquals(listOf("Alice", "Bob"), books.single().authors)
  }

  @Test
  fun `Kindle ZIP では ownership JSON だけを蔵書として読む`() {
    val bytes = zipOf(
      "Kindle/Digital.Content.Ownership.3.json" to """
        {
          "rightAction": "Grant",
          "timestamp": "2026-03-01T00:00:00Z",
          "asin": "KINDLE3",
          "title": "Zipped Kindle Book",
          "contentType": "Kindle E-Book"
        }
      """.trimIndent(),
      "Kindle/Kindle.Devices.ReadingSession.csv" to
        "Title,ASIN,content_type\nReading Log,KINDLE_LOG,E-Book\n",
      "Kindle/BookRelation.csv" to "Title,ASIN\nSeries Relation,KINDLE_RELATION\n",
    )

    val books = importer.parse(LibrarySource.KINDLE, "kindle-export.zip", bytes)

    assertEquals(listOf("Zipped Kindle Book"), books.map { it.title })
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Kindle の別 ownership ファイルに後続 revoke があれば所有扱いしない`() {
    val bytes = zipOf(
      "Kindle/Digital.Content.Ownership.1.json" to """
        {
          "rightType": "GRANT",
          "eventTimestamp": "2026-01-01T00:00:00Z",
          "asin": "KINDLE4",
          "title": "Returned Later",
          "contentType": "E-Book"
        }
      """.trimIndent(),
      "Kindle/Digital.Content.Ownership.2.json" to """
        {
          "rightType": "REVOKE",
          "eventTimestamp": "2026-02-01T00:00:00Z",
          "asin": "KINDLE4",
          "title": "Returned Later",
          "contentType": "E-Book"
        }
      """.trimIndent(),
    )

    importer.parse(LibrarySource.KINDLE, "kindle-export.zip", bytes)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Kindle の複数ファイル ZIP に ownership JSON が無ければ行動ログを蔵書扱いしない`() {
    val bytes = zipOf(
      "Kindle/Kindle.Devices.ReadingSession.csv" to "Title,ASIN\nReading Log,KINDLE_LOG\n",
      "Kindle/BookRelation.csv" to "Title,ASIN\nSeries Relation,KINDLE_RELATION\n",
    )

    importer.parse(LibrarySource.KINDLE, "kindle-export.zip", bytes)
  }

  @Test
  fun `Audible ZIP では Library csv だけを蔵書として読む`() {
    val bytes = zipOf(
      "Audible/Library.csv" to
        "Title,Author,Narrator,ASIN,Release Date,Deleted\n" +
        "Audio Book,Writer,Reader,AUDIO1,2026-01-02,false\n" +
        "Deleted Audio,Writer,Reader,AUDIO2,2026-01-03,true\n",
      "Audible/Listening History.csv" to "Title,ASIN\nListened Only,LISTEN1\n",
      "Audible/Purchase History.csv" to "Title,ASIN\nPurchase History Item,PURCHASE1\n",
      "Audible/Wishlist.csv" to "Title,ASIN\nWishlist Item,WISH1\n",
    )

    val books = importer.parse(LibrarySource.AUDIBLE, "audible-export.zip", bytes)

    assertEquals(1, books.size)
    assertEquals("AUDIO1", books.single().sourceId)
    assertEquals("Audio Book", books.single().title)
    assertEquals(listOf("Writer"), books.single().authors)
    assertEquals("2026-01-02", books.single().publishedDate)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Audible Wishlist csv を単体で選択しても蔵書として読まない`() {
    importer.parse(
      LibrarySource.AUDIBLE,
      "Wishlist.csv",
      "Title,ASIN\nWishlist Item,WISH1\n".toByteArray(),
    )
  }

  @Test
  fun `旧形式の Kindle CSV を単体選択した場合は引き続き解析する`() {
    val csv = """
      ﻿Title,Authors,ASIN,Publisher,Description,ISBN13
      "Book, One","Alice; Bob",B001,Example,"line1
      line2",9781234567890
    """.trimIndent()

    val books = importer.parse(LibrarySource.KINDLE, "kindle.csv", csv.toByteArray())

    assertEquals(1, books.size)
    assertEquals("B001", books.single().sourceId)
    assertEquals("Book, One", books.single().title)
    assertEquals(listOf("Alice", "Bob"), books.single().authors)
    assertEquals("line1\nline2", books.single().description)
    assertEquals("9781234567890", books.single().isbn13)
  }

  @Test
  fun `ASIN が無い場合は同じ入力から安定した ID を生成する`() {
    val csv = "Title,Authors,Publication Date\nNo Id Book,Writer,2025-03-01\n"

    val first = importer.parse(LibrarySource.KINDLE, "books.csv", csv.toByteArray()).single()
    val second = importer.parse(LibrarySource.KINDLE, "books.csv", csv.toByteArray()).single()

    assertTrue(first.sourceId.startsWith("derived:"))
    assertEquals(first.sourceId, second.sourceId)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `認識できない形式では既存蔵書を置換するための空リストを返さない`() {
    importer.parse(LibrarySource.KINDLE, "unknown.csv", "foo,bar\na,b\n".toByteArray())
  }

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
