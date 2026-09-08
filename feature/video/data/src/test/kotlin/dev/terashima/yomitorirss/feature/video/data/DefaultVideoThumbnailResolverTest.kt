package dev.terashima.yomitorirss.feature.video.data

import android.net.Uri
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultVideoThumbnailResolverTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `同じ動画versionのサムネイルcacheがあればSMBを再読込しない`() = runBlocking {
    val cacheDirectory = temporaryFolder.newFolder("cache")
    val item = VideoItem(
      id = "video-1",
      source = VideoSource.SMB,
      sourceId = "mosaic-smb-video://file?serverId=server&share=media&path=movie.mp4",
      title = "テスト動画",
      sizeBytes = 1_234L,
      updatedAtEpochMillis = 123L,
    )
    val thumbnailFile = File(cacheDirectory, "video-thumbnails/${item.id}-${item.sizeBytes}.jpg")
    check(thumbnailFile.parentFile?.mkdirs() == true)
    thumbnailFile.writeBytes(byteArrayOf(1, 2, 3))
    var opened = false
    val resolver = DefaultVideoThumbnailResolver(
      byteSourceFactory = object : VideoByteSourceFactory {
        override fun open(sourceId: String): VideoByteSource {
          opened = true
          error("cache利用時にSMBを開いてはいけません")
        }
      },
      cacheDirectory = cacheDirectory,
      ioDispatcher = Dispatchers.Unconfined,
    )

    val resolved = resolver.resolve(item)

    assertEquals(Uri.fromFile(thumbnailFile).toString(), resolved)
    assertFalse(opened)
  }

  @Test
  fun `SMB以外の動画は既存サムネイルURLをそのまま返す`() = runBlocking {
    val resolver = DefaultVideoThumbnailResolver(
      byteSourceFactory = object : VideoByteSourceFactory {
        override fun open(sourceId: String): VideoByteSource = error("SMBを開いてはいけません")
      },
      cacheDirectory = temporaryFolder.newFolder("web-cache"),
      ioDispatcher = Dispatchers.Unconfined,
    )
    val item = VideoItem(
      id = "web-1",
      source = VideoSource.WEB,
      sourceId = "https://example.invalid/video",
      title = "テスト動画",
      thumbnailUrl = "https://example.invalid/thumbnail.jpg",
      updatedAtEpochMillis = 1L,
    )

    assertEquals(item.thumbnailUrl, resolver.resolve(item))
  }
}
