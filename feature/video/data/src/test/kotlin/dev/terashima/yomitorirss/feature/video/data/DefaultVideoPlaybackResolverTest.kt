package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultVideoPlaybackResolverTest {
  @Test
  fun `Web抽出ルールがなければページ表示へfallbackする`() = runBlocking {
    val resolver = resolver(FakeSmbAccess())
    val item = item(
      source = VideoSource.WEB,
      sourceId = "https://example.com/watch/1",
      pageUrl = "https://example.com/watch/1",
    )

    assertEquals(
      VideoPlaybackTarget.WebPage("https://example.com/watch/1"),
      resolver.resolve(item),
    )
  }

  @Test
  fun `WebストリームのRefererは元ページのoriginだけに制限する`() {
    assertEquals(
      "https://example.com/",
      webStreamReferrerUrl("https://example.com/watch/1?token=secret#player"),
    )
    assertEquals(
      "https://example.com:8443/",
      webStreamReferrerUrl("https://example.com:8443/watch/1"),
    )
  }

  @Test
  fun `HTTP以外のURLはWebストリームRefererにしない`() {
    assertNull(webStreamReferrerUrl("file:///tmp/video.html"))
    assertNull(webStreamReferrerUrl("not a url"))
  }

  @Test
  fun `SMB動画IDは特殊文字を含むserverとpathを往復できる`() {
    val location = parseSmbVideoSourceId(
      smbVideoSourceId("server & 1", "videos\\A+B movie #1.mkv"),
    )

    assertEquals("server & 1", location.serverId)
    assertEquals("videos\\A+B movie #1.mkv", location.path)
  }

  @Test
  fun `SMB再生先はcatalogの長さを利用しbyte sourceはoffset readを委譲する`() = runBlocking {
    val smb = FakeSmbAccess(payload = "0123456789".toByteArray())
    val sourceId = smbVideoSourceId("server-1", "videos\\movie.mkv")
    val resolver = resolver(smb)
    val item = item(
      source = VideoSource.SMB,
      sourceId = sourceId,
      sizeBytes = 10L,
      mimeType = "video/x-matroska",
    )

    assertEquals(
      VideoPlaybackTarget.Smb(sourceId, 10L, "video/x-matroska"),
      resolver.resolve(item),
    )
    assertEquals(0, smb.openCount)

    val buffer = ByteArray(4)
    resolver.open(sourceId).use { source ->
      assertEquals(10L, source.length)
      assertEquals(4, source.read(position = 3L, buffer = buffer, offset = 0, length = 4))
    }
    assertArrayEquals("3456".toByteArray(), buffer)
    assertEquals("server-1", smb.lastServerId)
    assertEquals("videos\\movie.mkv", smb.lastPath)
    assertTrue(smb.closed)
  }

  private fun resolver(smb: SmbMediaFileAccess) = DefaultVideoPlaybackResolver(
    smbMediaFileAccess = smb,
    rules = { emptyList() },
    webExtractorClient = AndroidWebVideoExtractorClient { null },
  )

  private fun item(
    source: VideoSource,
    sourceId: String,
    pageUrl: String? = null,
    sizeBytes: Long? = null,
    mimeType: String? = null,
  ) = VideoItem(
    id = "video-1",
    source = source,
    sourceId = sourceId,
    title = "video",
    pageUrl = pageUrl,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    updatedAtEpochMillis = 1L,
  )
}

private class FakeSmbAccess(
  private val payload: ByteArray = ByteArray(0),
) : SmbMediaFileAccess {
  var openCount = 0
    private set
  var lastServerId: String? = null
    private set
  var lastPath: String? = null
    private set
  var closed = false
    private set

  override suspend fun listMediaFiles(extensions: Set<String>): List<SmbMediaFile> = emptyList()

  override fun openMediaFile(serverId: String, path: String): SmbMediaReadHandle {
    openCount += 1
    lastServerId = serverId
    lastPath = path
    return object : SmbMediaReadHandle {
      override val size: Long = payload.size.toLong()

      override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= payload.size) return -1
        val count = minOf(length, payload.size - position.toInt())
        payload.copyInto(buffer, destinationOffset = offset, startIndex = position.toInt(), endIndex = position.toInt() + count)
        return count
      }

      override fun close() {
        closed = true
      }
    }
  }
}
