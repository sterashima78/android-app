package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SmbVideoSourcePoolTest {
  @Test
  fun `シーク相当のcloseと再openでは同じbyte sourceを再利用する`() {
    val factory = RecordingByteSourceFactory()
    val pool = SmbVideoSourcePool(factory)

    val first = pool.acquire("video-1")
    val firstSource = first.source
    first.close()

    val second = pool.acquire("video-1")

    assertSame(firstSource, second.source)
    assertEquals(1, factory.openCount)
    assertEquals(0, factory.sources.single().closeCount)

    second.close()
    pool.close()

    assertEquals(1, factory.sources.single().closeCount)
  }

  @Test
  fun `小さい連続readは一つのread ahead windowを共有する`() {
    val factory = RecordingByteSourceFactory()
    val pool = SmbVideoSourcePool(factory)
    val buffer = ByteArray(16)

    val first = pool.acquire("video-1")
    assertEquals(8, first.read(0L, buffer, 0, 8))
    first.close()

    val reopened = pool.acquire("video-1")
    assertEquals(8, reopened.read(32L, buffer, 0, 8))

    val source = factory.sources.single()
    assertEquals(1, source.readCalls.size)
    assertEquals(0L, source.readCalls.single().position)
    assertEquals(SMB_VIDEO_READ_AHEAD_BYTES, source.readCalls.single().length)

    reopened.close()
    pool.close()
  }

  @Test
  fun `read ahead window外へのseekでは新しいSMB readを行う`() {
    val factory = RecordingByteSourceFactory()
    val pool = SmbVideoSourcePool(factory)
    val lease = pool.acquire("video-1")
    val buffer = ByteArray(8)

    assertEquals(8, lease.read(0L, buffer, 0, 8))
    val seekPosition = SMB_VIDEO_READ_AHEAD_BYTES.toLong()
    assertEquals(8, lease.read(seekPosition, buffer, 0, 8))

    val source = factory.sources.single()
    assertEquals(2, source.readCalls.size)
    assertEquals(seekPosition, source.readCalls.last().position)

    lease.close()
    pool.close()
  }

  @Test
  fun `読込失敗で破棄したsourceは次回取得時に開き直す`() {
    val factory = RecordingByteSourceFactory()
    val pool = SmbVideoSourcePool(factory)

    val first = pool.acquire("video-1")
    first.invalidate()
    first.close()

    val second = pool.acquire("video-1")

    assertEquals(2, factory.openCount)
    assertEquals(1, factory.sources.first().closeCount)

    second.close()
    pool.close()

    assertEquals(1, factory.sources.last().closeCount)
  }
}

private class RecordingByteSourceFactory : VideoByteSourceFactory {
  val sources = mutableListOf<RecordingByteSource>()
  val openCount: Int get() = sources.size

  override fun open(sourceId: String): VideoByteSource = RecordingByteSource().also(sources::add)
}

private class RecordingByteSource : VideoByteSource {
  override val length: Long = SMB_VIDEO_READ_AHEAD_BYTES.toLong() * 2
  val readCalls = mutableListOf<ReadCall>()
  var closeCount = 0
    private set

  override fun read(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int,
  ): Int {
    readCalls += ReadCall(position, length)
    return minOf(length, (this.length - position).coerceAtLeast(0L).toInt())
  }

  override fun close() {
    closeCount += 1
  }
}

private data class ReadCall(
  val position: Long,
  val length: Int,
)
