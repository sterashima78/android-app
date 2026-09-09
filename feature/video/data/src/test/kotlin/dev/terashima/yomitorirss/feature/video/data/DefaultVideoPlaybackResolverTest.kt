package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackDiagnostics
import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackReferrerSource
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
  fun `WebストリームのRefererは既定で元ページのoriginだけに制限する`() {
    assertEquals(
      "https://example.com/",
      webStreamReferrerUrl("https://example.com/watch/1?token=fixture#player"),
    )
    assertEquals(
      "https://example.com:8443/",
      webStreamReferrerUrl("https://example.com:8443/watch/1"),
    )
  }

  @Test
  fun `opt-in時はextractor指定Refererのpathだけを追加共有する`() {
    assertEquals(
      "https://player.example.net/embed/123",
      webStreamReferrerUrl(
        pageUrl = "https://page.example.com/watch/1",
        preferredReferrerUrl = "https://player.example.net/embed/123?token=fixture#player",
        shareReferrerPath = true,
      ),
    )
  }

  @Test
  fun `opt-in時もRefererからuserinfo query fragmentを除去する`() {
    assertEquals(
      "https://player.example.net:8443/embed/123",
      webVideoPlaybackReferrerUrl(
        referrerUrl = "https://user:pass@player.example.net:8443/embed/123?token=fixture#player",
        shareReferrerPath = true,
      ),
    )
  }

  @Test
  fun `path共有OFFならextractor指定Refererもoriginだけにする`() {
    assertEquals(
      "https://player.example.net/",
      webStreamReferrerUrl(
        pageUrl = "https://page.example.com/watch/1",
        preferredReferrerUrl = "https://player.example.net/embed/123?token=fixture",
        shareReferrerPath = false,
      ),
    )
  }

  @Test
  fun `HTTP以外のURLはWebストリームRefererにしない`() {
    assertNull(webStreamReferrerUrl("file:///tmp/video.html"))
    assertNull(webStreamReferrerUrl("not a url"))
  }

  @Test
  fun `実requestのRefererを観測した場合は実requestを参照元経路とする`() {
    assertEquals(
      WebVideoPlaybackReferrerSource.OBSERVED_REQUEST,
      webVideoPlaybackReferrerSource(
        diagnostics = WebVideoPlaybackDiagnostics(streamRequestRefererObserved = true),
        selectedReferrerUrl = "https://player.example.net/",
      ),
    )
  }

  @Test
  fun `実requestのReferer未観測で参照元URLがあればextractor指定とする`() {
    assertEquals(
      WebVideoPlaybackReferrerSource.EXTRACTOR,
      webVideoPlaybackReferrerSource(
        diagnostics = WebVideoPlaybackDiagnostics(streamRequestObserved = false),
        selectedReferrerUrl = "https://player.example.net/embed/1",
      ),
    )
  }

  @Test
  fun `参照元URLがなければ元ページを参照元経路とする`() {
    assertEquals(
      WebVideoPlaybackReferrerSource.PAGE,
      webVideoPlaybackReferrerSource(
        diagnostics = WebVideoPlaybackDiagnostics(),
        selectedReferrerUrl = null,
      ),
    )
  }

  @Test
  fun `SMB動画IDは特殊文字を含むserver share pathを往復できる`() {
    val location = parseSmbVideoSourceId(
      smbVideoSourceId("server & 1", "media + #1", "videos\\A+B movie #1.mkv"),
    )

    assertEquals("server & 1", location.serverId)
    assertEquals("media + #1", location.share)
    assertEquals("videos\\A+B movie #1.mkv", location.path)
  }

  @Test
  fun `旧SMB動画IDはshareなしで読み取れる`() {
    val location = parseSmbVideoSourceId(
      "mosaic-smb-video://file?serverId=server-1&path=videos%5Cmovie.mkv",
    )

    assertEquals("server-1", location.serverId)
    assertNull(location.share)
    assertEquals("videos\\movie.mkv", location.path)
  }

  @Test
  fun `SMB再生先はcatalogの長さを利用し設定済みshare rootでoffset readを委譲する`() = runBlocking {
    val smb = FakeSmbAccess(payload = "0123456789".toByteArray())
    val sourceId = smbVideoSourceId("server-1", "media", "videos\\movie.mkv")
    val resolver = resolver(
      smb = smb,
      smbSources = listOf(
        VideoSmbSource(
          id = "source-1",
          serverId = "server-1",
          share = "media",
          rootPath = "videos",
        ),
      ),
    )
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
    assertEquals(SmbMediaLocation("server-1", "media", "videos"), smb.lastLocation)
    assertEquals("videos\\movie.mkv", smb.lastPath)
    assertTrue(smb.closed)
  }

  @Test
  fun `SMB動画のsize fallbackはIO dispatcher経由でopenする`() = runBlocking {
    val smb = FakeSmbAccess(payload = "0123".toByteArray())
    val dispatcher = RecordingDispatcher()
    val sourceId = smbVideoSourceId("server-1", "media", "videos\\movie.mkv")
    val resolver = resolver(
      smb = smb,
      smbSources = listOf(
        VideoSmbSource(
          id = "source-1",
          serverId = "server-1",
          share = "media",
          rootPath = "videos",
        ),
      ),
      ioDispatcher = dispatcher,
    )

    assertEquals(
      VideoPlaybackTarget.Smb(sourceId, 4L, null),
      resolver.resolve(
        item(
          source = VideoSource.SMB,
          sourceId = sourceId,
          sizeBytes = null,
        ),
      ),
    )
    assertEquals(1, dispatcher.dispatchCount)
    assertEquals(1, smb.openCount)
  }

  @Test
  fun `旧SMB動画IDはpathを含む設定から再生できる`() {
    val smb = FakeSmbAccess(payload = "0123".toByteArray())
    val resolver = resolver(
      smb = smb,
      smbSources = listOf(
        VideoSmbSource(
          id = "source-1",
          serverId = "server-1",
          share = "media",
          rootPath = "videos",
        ),
      ),
    )
    val sourceId = "mosaic-smb-video://file?serverId=server-1&path=videos%5Cmovie.mkv"

    resolver.open(sourceId).use { source ->
      assertEquals(4L, source.length)
    }

    assertEquals(SmbMediaLocation("server-1", "media", "videos"), smb.lastLocation)
  }

  private fun resolver(
    smb: SmbMediaFileAccess,
    smbSources: List<VideoSmbSource> = emptyList(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  ) = DefaultVideoPlaybackResolver(
    smbMediaFileAccess = smb,
    smbSources = { smbSources },
    rules = { emptyList() },
    webExtractorClient = AndroidWebVideoExtractorClient { null },
    ioDispatcher = ioDispatcher,
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
  var lastLocation: SmbMediaLocation? = null
    private set
  var lastPath: String? = null
    private set
  var closed = false
    private set

  override suspend fun listMediaFiles(
    location: SmbMediaLocation,
    extensions: Set<String>,
  ): List<SmbMediaFile> = emptyList()

  override fun openMediaFile(
    location: SmbMediaLocation,
    path: String,
  ): SmbMediaReadHandle {
    openCount += 1
    lastLocation = location
    lastPath = path
    return object : SmbMediaReadHandle {
      override val size: Long = payload.size.toLong()

      override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= payload.size) return -1
        val count = minOf(length, payload.size - position.toInt())
        payload.copyInto(
          buffer,
          destinationOffset = offset,
          startIndex = position.toInt(),
          endIndex = position.toInt() + count,
        )
        return count
      }

      override fun close() {
        closed = true
      }
    }
  }
}

private class RecordingDispatcher : CoroutineDispatcher() {
  var dispatchCount: Int = 0
    private set

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    dispatchCount += 1
    block.run()
  }
}
