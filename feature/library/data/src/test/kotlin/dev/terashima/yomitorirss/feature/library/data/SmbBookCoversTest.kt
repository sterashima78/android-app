package dev.terashima.yomitorirss.feature.library.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

  @Test
  fun `ZIP走査上限までに画像へ到達できなければ表紙候補を返さない`() {
    val archive = zipOf(
      "metadata.txt" to ByteArray(4096) { index -> (index % 251).toByte() },
      "pages/001.jpg" to byteArrayOf(1, 2, 3),
    )

    assertNull(extractFirstZipImage(ByteArrayInputStream(archive), maxBytes = 16))
  }

  @Test
  fun `表紙キャッシュは古いものから上限内になるまで削除する`() {
    val entries = listOf(
      SmbCoverCacheEntry(path = "/cover/old.jpg", size = 40, lastModified = 100),
      SmbCoverCacheEntry(path = "/cover/middle.jpg", size = 40, lastModified = 200),
      SmbCoverCacheEntry(path = "/cover/new.jpg", size = 40, lastModified = 300),
    )

    assertEquals(
      listOf("/cover/old.jpg", "/cover/middle.jpg"),
      smbCoverCachePathsToEvict(entries, maxBytes = 50),
    )
  }

  @Test
  fun `新しく生成した表紙はLRU削除対象から保護する`() {
    val entries = listOf(
      SmbCoverCacheEntry(path = "/cover/protected.jpg", size = 40, lastModified = 100),
      SmbCoverCacheEntry(path = "/cover/middle.jpg", size = 40, lastModified = 200),
      SmbCoverCacheEntry(path = "/cover/new.jpg", size = 40, lastModified = 300),
    )

    assertEquals(
      listOf("/cover/middle.jpg", "/cover/new.jpg"),
      smbCoverCachePathsToEvict(
        entries = entries,
        maxBytes = 50,
        protectedPath = "/cover/protected.jpg",
      ),
    )
  }

  @Test
  fun `表紙キャッシュが上限内なら削除しない`() {
    val entries = listOf(
      SmbCoverCacheEntry(path = "/cover/a.jpg", size = 20, lastModified = 100),
      SmbCoverCacheEntry(path = "/cover/b.jpg", size = 30, lastModified = 200),
    )

    assertEquals(emptyList<String>(), smbCoverCachePathsToEvict(entries, maxBytes = 50))
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
