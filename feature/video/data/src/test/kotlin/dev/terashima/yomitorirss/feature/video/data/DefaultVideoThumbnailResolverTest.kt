package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultVideoThumbnailResolverTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

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
      context = context,
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
      context = context,
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

  @Test
  fun `サムネイルDataSourceは指定offsetを読みsourceの寿命を所有しない`() {
    val source = RecordingByteSource(byteArrayOf(10, 20, 30, 40, 50))
    val dataSource = ThumbnailVideoDataSource(source)
    val uri = Uri.parse("mosaic-video://thumbnail/video")

    val remaining = dataSource.open(
      DataSpec.Builder()
        .setUri(uri)
        .setPosition(1L)
        .setLength(3L)
        .build(),
    )
    val buffer = ByteArray(4)

    assertEquals(3L, remaining)
    assertEquals(3, dataSource.read(buffer, 0, buffer.size))
    assertArrayEquals(byteArrayOf(20, 30, 40, 0), buffer)
    assertEquals(C.RESULT_END_OF_INPUT, dataSource.read(buffer, 0, buffer.size))

    dataSource.close()
    assertFalse(source.closed)
    source.close()
    assertTrue(source.closed)
  }
}

private class RecordingByteSource(
  private val bytes: ByteArray,
) : VideoByteSource {
  var closed = false
    private set

  override val length: Long get() = bytes.size.toLong()

  override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
    check(!closed)
    if (position >= bytes.size) return -1
    val count = minOf(length, bytes.size - position.toInt())
    bytes.copyInto(
      destination = buffer,
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
