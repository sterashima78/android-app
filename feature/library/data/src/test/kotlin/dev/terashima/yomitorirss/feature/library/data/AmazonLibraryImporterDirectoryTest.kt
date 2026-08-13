package dev.terashima.yomitorirss.feature.library.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AmazonLibraryImporterDirectoryTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `ownership ディレクトリ配下の JSON を Kindle 蔵書として読む`() {
    val bytes = zipOf(
      "All Data Categories/Digital.Content.Ownership/0001.json" to """
        {
          "rightType": "GRANT",
          "eventTimestamp": "2026-08-13T00:00:00Z",
          "asin": "DIRBOOK001",
          "title": "Directory Kindle Book",
          "contentType": "Kindle E-Book"
        }
      """.trimIndent(),
      "All Data Categories/Other.Category/0001.json" to """
        {
          "rightType": "GRANT",
          "asin": "OTHER00001",
          "title": "Not Kindle Ownership",
          "contentType": "Kindle E-Book"
        }
      """.trimIndent(),
    )

    val books = ByteArrayInputStream(bytes).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(1, books.size)
    assertEquals("DIRBOOK001", books.single().sourceId)
    assertEquals("Directory Kindle Book", books.single().title)
  }

  @Test
  fun `従来の ownership ファイル名形式も引き続き読む`() {
    val bytes = zipOf(
      "Kindle/Digital.Content.Ownership.9.json" to """
        {
          "rightType": "GRANT",
          "asin": "FLATBOOK01",
          "title": "Flat Kindle Book",
          "contentType": "Kindle E-Book"
        }
      """.trimIndent(),
    )

    val books = ByteArrayInputStream(bytes).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(listOf("Flat Kindle Book"), books.map { it.title })
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
