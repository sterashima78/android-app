package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AmazonLibraryImporterObservedSchemaTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `resource と rights を持つ実エクスポート構造を解析する`() {
    val json = """
      {
        "rights": [
          {
            "rightType": "Download",
            "origin": { "originType": "ExampleOrigin" },
            "rightStatus": "Active",
            "acquiredDate": "2026-01-02T03:04:05Z",
            "consumptions": []
          }
        ],
        "lastUpdatedDate": "2026-01-03T03:04:05Z",
        "resource": {
          "resourceType": "KindleEBook",
          "catalog": "Amazon",
          "ASIN": "EXAMPLE001",
          "Product Name": "Observed Schema Book"
        },
        "entity": { "entityType": "Customer" }
      }
    """.trimIndent()

    val books = importer.parse(
      source = LibrarySource.KINDLE,
      fileName = "Digital.Content.Ownership.10.json",
      bytes = json.toByteArray(),
    )

    assertEquals(1, books.size)
    assertEquals("EXAMPLE001", books.single().sourceId)
    assertEquals("Observed Schema Book", books.single().title)
  }

  @Test
  fun `rights が複数要素でも Active な権利を認識する`() {
    val json = """
      {
        "rights": [
          {
            "rightType": "Preview",
            "rightStatus": "Inactive",
            "acquiredDate": "2025-12-01T00:00:00Z"
          },
          {
            "rightType": "Download",
            "rightStatus": "Active",
            "acquiredDate": "2026-01-01T00:00:00Z"
          }
        ],
        "lastUpdatedDate": "2026-01-02T00:00:00Z",
        "resource": {
          "resourceType": "KindleEBook",
          "ASIN": "EXAMPLE002",
          "Product Name": "Multiple Rights Book"
        },
        "entity": { "entityType": "Customer" }
      }
    """.trimIndent()

    val books = importer.parse(
      source = LibrarySource.KINDLE,
      fileName = "Digital.Content.Ownership.11.json",
      bytes = json.toByteArray(),
    )

    assertEquals(listOf("Multiple Rights Book"), books.map { it.title })
  }

  @Test
  fun `Inactive のみの権利と非書籍 resourceType は蔵書から除外する`() {
    val active = """
      {
        "rights": [{ "rightType": "Download", "rightStatus": "Active" }],
        "lastUpdatedDate": "2026-01-03T00:00:00Z",
        "resource": {
          "resourceType": "KindleEBook",
          "ASIN": "EXAMPLE003",
          "Product Name": "Active Book"
        }
      }
    """.trimIndent()
    val inactive = """
      {
        "rights": [{ "rightType": "Download", "rightStatus": "Inactive" }],
        "lastUpdatedDate": "2026-01-04T00:00:00Z",
        "resource": {
          "resourceType": "KindleEBook",
          "ASIN": "EXAMPLE004",
          "Product Name": "Inactive Book"
        }
      }
    """.trimIndent()
    val music = """
      {
        "rights": [{ "rightType": "Download", "rightStatus": "Active" }],
        "resource": {
          "resourceType": "MusicAlbum",
          "ASIN": "EXAMPLE005",
          "Product Name": "Not A Kindle Book"
        }
      }
    """.trimIndent()

    val bytes = zipOf(
      "nested/a/Digital.Content.Ownership.1.json" to active,
      "nested/b/Digital.Content.Ownership.2.json" to inactive,
      "nested/c/Digital.Content.Ownership.3.json" to music,
    )

    val books = importer.parse(LibrarySource.KINDLE, "amazon-export.zip", bytes)

    assertEquals(listOf("Active Book"), books.map { it.title })
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
