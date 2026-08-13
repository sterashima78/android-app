package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
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

  @Test
  fun `Kindle ZIP は100件を超える無関係ファイルをストリームで読み飛ばす`() {
    val files = buildList {
      repeat(150) { index ->
        add("Other/data-$index.json" to "{\"ignored\":$index}")
      }
      add(
        "Kindle/Digital.Content.Ownership.151.json" to """
          {
            "rightAction": "Grant",
            "timestamp": "2026-04-01T00:00:00Z",
            "asin": "KINDLE151",
            "title": "Streaming Kindle Book",
            "contentType": "Kindle E-Book"
          }
        """.trimIndent(),
      )
    }
    val bytes = zipOf(files)

    val books = ByteArrayInputStream(bytes).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(listOf("Streaming Kindle Book"), books.map { it.title })
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
  fun `Kindle ZIP に ownership JSON が無ければ他ファイルへフォールバックしない`() {
    val bytes = zipOf(
      "Kindle/Kindle.Devices.ReadingSession.csv" to "Title,ASIN\nReading Log,KINDLE_LOG\n",
      "Kindle/BookRelation.csv" to "Title,ASIN\nSeries Relation,KINDLE_RELATION\n",
    )

    importer.parse(LibrarySource.KINDLE, "kindle-export.zip", bytes)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Kindle は旧 CSV を受け付けない`() {
    importer.parse(
      LibrarySource.KINDLE,
      "kindle.csv",
      "Title,ASIN\nLegacy Book,KINDLE5\n".toByteArray(),
    )
  }

  @Test
  fun `Audible の Library csv を直接解析する`() {
    val csv =
      "Title,Author,Narrator,ASIN,Release Date,Deleted\n" +
        "Audio Book,Writer,Reader,AUDIO1,2026-01-02,false\n" +
        "Deleted Audio,Writer,Reader,AUDIO2,2026-01-03,true\n"

    val books = importer.parse(LibrarySource.AUDIBLE, "Library.csv", csv.toByteArray())

    assertEquals(1, books.size)
    assertEquals("AUDIO1", books.single().sourceId)
    assertEquals("Audio Book", books.single().title)
    assertEquals(listOf("Writer"), books.single().authors)
    assertEquals("2026-01-02", books.single().publishedDate)
  }

  @Test
  fun `Audible ZIP では Library csv だけを蔵書として読む`() {
    val bytes = zipOf(
      "Audible/Library.csv" to "Title,Author,ASIN\nAudio Book,Writer,AUDIO1\n",
      "Audible/Listening History.csv" to "Title,ASIN\nListened Only,LISTEN1\n",
      "Audible/Purchase History.csv" to "Title,ASIN\nPurchase History Item,PURCHASE1\n",
      "Audible/Wishlist.csv" to "Title,ASIN\nWishlist Item,WISH1\n",
    )

    val books = importer.parse(LibrarySource.AUDIBLE, "audible-export.zip", bytes)

    assertEquals(listOf("Audio Book"), books.map { it.title })
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Audible Wishlist csv を単体で選択しても蔵書として読まない`() {
    importer.parse(
      LibrarySource.AUDIBLE,
      "Wishlist.csv",
      "Title,ASIN\nWishlist Item,WISH1\n".toByteArray(),
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Audible は Library tsv を受け付けない`() {
    importer.parse(
      LibrarySource.AUDIBLE,
      "Library.tsv",
      "Title\tASIN\nAudio Book\tAUDIO1\n".toByteArray(),
    )
  }

  private fun zipOf(vararg files: Pair<String, String>): ByteArray = zipOf(files.toList())

  private fun zipOf(files: List<Pair<String, String>>): ByteArray =
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
