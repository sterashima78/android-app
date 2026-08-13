package dev.terashima.yomitorirss.feature.library.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonLibraryImporterRecursiveZipTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `深いディレクトリの ownership ファイルを読む`() {
    val bytes = zipOf(
      "root/one/two/three/four/Digital.Content.Ownership.9.json" to ownershipJson(
        asin = "DEEPBOOK01",
        title = "Deep Kindle Book",
      ),
    )

    val books = ByteArrayInputStream(bytes).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(listOf("Deep Kindle Book"), books.map { it.title })
  }

  @Test
  fun `ZIP 内の ZIP も再帰的に探索する`() {
    val innerZip = zipOf(
      "payload/account/archive/Digital.Content.Ownership.9.json" to ownershipJson(
        asin = "NESTEDBOOK01",
        title = "Nested Kindle Book",
      ),
    )
    val outerZip = binaryZipOf(
      "exports/part-01.zip" to innerZip,
      "exports/readme.txt" to "ignored".toByteArray(),
    )

    val books = ByteArrayInputStream(outerZip).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(listOf("Nested Kindle Book"), books.map { it.title })
  }

  @Test
  fun `パス名に Kindle を含む解析不能ファイルがあっても他の ownership を読む`() {
    val bytes = zipOf(
      "Kindle/Digital.Content.Ownership.1.json" to "{\"unknown\":true}",
      "All Data Categories/history/Digital.Content.Ownership.9.json" to ownershipJson(
        asin = "FALLBACKBOOK01",
        title = "Unscoped Kindle Book",
      ),
    )

    val books = ByteArrayInputStream(bytes).use { input ->
      importer.parseKindle("amazon-export.zip", input)
    }

    assertEquals(listOf("Unscoped Kindle Book"), books.map { it.title })
  }

  @Test
  fun `ownership ファイルを発見したが解析できない場合は原因を区別する`() {
    val bytes = zipOf(
      "root/Digital.Content.Ownership.9.json" to "{\"unknown\":true}",
    )

    val error = runCatching {
      ByteArrayInputStream(bytes).use { input ->
        importer.parseKindle("amazon-export.zip", input)
      }
    }.exceptionOrNull()

    requireNotNull(error)
    assertTrue(error.message.orEmpty().contains("見つかりましたが Kindle 蔵書を解析できませんでした"))
  }

  private fun ownershipJson(asin: String, title: String): String = """
    {
      "rightType": "GRANT",
      "eventTimestamp": "2026-08-13T00:00:00Z",
      "asin": "$asin",
      "title": "$title",
      "contentType": "Kindle E-Book"
    }
  """.trimIndent()

  private fun zipOf(vararg files: Pair<String, String>): ByteArray =
    binaryZipOf(*files.map { (name, text) -> name to text.toByteArray() }.toTypedArray())

  private fun binaryZipOf(vararg files: Pair<String, ByteArray>): ByteArray =
    ByteArrayOutputStream().use { output ->
      ZipOutputStream(output).use { zip ->
        files.forEach { (name, bytes) ->
          zip.putNextEntry(ZipEntry(name))
          zip.write(bytes)
          zip.closeEntry()
        }
      }
      output.toByteArray()
    }
}
