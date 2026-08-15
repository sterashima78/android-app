package dev.terashima.yomitorirss.feature.library.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbBookCoversTest {
  @Test
  fun `ZIP内の画像以外を飛ばして最初の画像を返す`() {
    val firstImage = byteArrayOf(1, 2, 3, 4)
    val secondImage = byteArrayOf(5, 6, 7)
    val archive = zipOf(
      "metadata.txt" to "metadata".toByteArray(),
      "pages/001.jpg" to firstImage,
      "pages/002.png" to secondImage,
    )

    assertArrayEquals(
      firstImage,
      extractFirstZipImage(ByteArrayInputStream(archive), maxBytes = 1024 * 1024),
    )
  }

  @Test
  fun `ZIP内に画像がなければ表紙候補を返さない`() {
    val archive = zipOf("metadata.txt" to "metadata".toByteArray())

    assertNull(extractFirstZipImage(ByteArrayInputStream(archive), maxBytes = 1024 * 1024))
  }

  private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
    ByteArrayOutputStream().use { output ->
      ZipOutputStream(output).use { zip ->
        entries.forEach { (name, bytes) ->
          zip.putNextEntry(ZipEntry(name))
          zip.write(bytes)
          zip.closeEntry()
        }
      }
      output.toByteArray()
    }
}