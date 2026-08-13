package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AmazonLibraryImporterSystemContentTest {
  private val importer = AmazonLibraryImporter()

  @Test
  fun `KindleDictionary origin の補助辞書は蔵書から除外する`() {
    val normalBook = ownershipJson("EXAMPLE101", "Example Novel", "Purchase")
    val dictionary = ownershipJson("EXAMPLE102", "Example Dictionary", "KindleDictionary")
    val books = importer.parse(
      LibrarySource.KINDLE,
      "amazon-export.zip",
      zipOf(
        "Digital.Content.Ownership.1.json" to normalBook,
        "Digital.Content.Ownership.2.json" to dictionary,
      ),
    )
    assertEquals(listOf("Example Novel"), books.map { it.title })
  }

  @Test
  fun `Kindle のユーザーガイドを除外し一般書籍の guide は残す`() {
    val originMarkedGuide = ownershipJson("EXAMPLE201", "Kindle Example Device Guide", "KindleUserGuide")
    val titleMarkedGuide = ownershipJson("EXAMPLE202", "Kindle Example Device User's Guide")
    val ordinaryBook = ownershipJson("EXAMPLE203", "A User Guide to Kotlin", "Purchase")
    val books = importer.parse(
      LibrarySource.KINDLE,
      "amazon-export.zip",
      zipOf(
        "Digital.Content.Ownership.1.json" to originMarkedGuide,
        "Digital.Content.Ownership.2.json" to titleMarkedGuide,
        "Digital.Content.Ownership.3.json" to ordinaryBook,
      ),
    )
    assertEquals(listOf("A User Guide to Kotlin"), books.map { it.title })
  }

  private fun ownershipJson(asin: String, title: String, originType: String? = null): String {
    val origin = originType?.let { "\"origin\":{\"originType\":\"$it\"}," }.orEmpty()
    return """{"rights":[{"rightType":"Download",$origin"rightStatus":"Active"}],"resource":{"resourceType":"KindleEBook","ASIN":"$asin","Product Name":"$title"}}"""
  }

  private fun zipOf(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
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
