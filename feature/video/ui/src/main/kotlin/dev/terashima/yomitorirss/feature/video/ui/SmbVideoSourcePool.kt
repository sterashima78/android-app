package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory

internal const val SMB_VIDEO_READ_AHEAD_BYTES = 1024 * 1024

/** Keeps SMB byte sources open across Media3 DataSource reopen cycles within one player lifetime. */
internal class SmbVideoSourcePool(
  private val byteSourceFactory: VideoByteSourceFactory,
) : AutoCloseable {
  private val entries = mutableMapOf<String, Entry>()
  private var closed = false

  fun acquire(sourceId: String): Lease = synchronized(this) {
    check(!closed) { "SMB動画のsource poolは既に閉じられています" }
    val entry = entries[sourceId] ?: Entry(
      ReadAheadVideoByteSource(byteSourceFactory.open(sourceId)),
    ).also {
      entries[sourceId] = it
    }
    entry.activeLeases += 1
    Lease(this, sourceId, entry)
  }

  override fun close() {
    val sourcesToClose = synchronized(this) {
      if (closed) return
      closed = true
      val closable = buildList {
        entries.values.forEach { entry ->
          entry.retired = true
          if (entry.activeLeases == 0) add(entry.source)
        }
      }
      entries.clear()
      closable
    }
    sourcesToClose.forEach(VideoByteSource::close)
  }

  private fun release(entry: Entry) {
    val sourceToClose = synchronized(this) {
      check(entry.activeLeases > 0) { "SMB動画のsource leaseが不正です" }
      entry.activeLeases -= 1
      entry.source.takeIf { entry.activeLeases == 0 && entry.retired }
    }
    sourceToClose?.close()
  }

  private fun invalidate(sourceId: String, entry: Entry) {
    val sourceToClose = synchronized(this) {
      if (entries[sourceId] === entry) entries.remove(sourceId)
      entry.retired = true
      entry.source.takeIf { entry.activeLeases == 0 }
    }
    sourceToClose?.close()
  }

  internal class Lease internal constructor(
    private val pool: SmbVideoSourcePool,
    private val sourceId: String,
    private val entry: Entry,
  ) : AutoCloseable {
    internal val source: VideoByteSource get() = entry.source
    private var released = false

    val length: Long
      get() {
        check(!released) { "SMB動画のsource leaseは既に閉じられています" }
        return entry.source.length
      }

    fun read(
      position: Long,
      buffer: ByteArray,
      offset: Int,
      length: Int,
    ): Int {
      check(!released) { "SMB動画のsource leaseは既に閉じられています" }
      return entry.source.read(position, buffer, offset, length)
    }

    fun invalidate() {
      check(!released) { "SMB動画のsource leaseは既に閉じられています" }
      pool.invalidate(sourceId, entry)
    }

    override fun close() {
      if (released) return
      released = true
      pool.release(entry)
    }
  }

  internal class Entry(
    val source: VideoByteSource,
    var activeLeases: Int = 0,
    var retired: Boolean = false,
  )
}

private class ReadAheadVideoByteSource(
  private val delegate: VideoByteSource,
) : VideoByteSource {
  private val cache = ByteArray(SMB_VIDEO_READ_AHEAD_BYTES)
  private var cacheStart = -1L
  private var cacheSize = 0
  private var closed = false

  override val length: Long
    get() = delegate.length

  @Synchronized
  override fun read(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int,
  ): Int {
    check(!closed) { "SMB動画は既に閉じられています" }
    if (length == 0) return 0
    if (position >= this.length) return -1

    if (cacheStart >= 0L && position >= cacheStart && position < cacheStart + cacheSize) {
      val cacheOffset = (position - cacheStart).toInt()
      val copied = minOf(length, cacheSize - cacheOffset)
      cache.copyInto(
        destination = buffer,
        destinationOffset = offset,
        startIndex = cacheOffset,
        endIndex = cacheOffset + copied,
      )
      return copied
    }

    val readAheadLength = minOf(
      SMB_VIDEO_READ_AHEAD_BYTES.toLong(),
      this.length - position,
    ).toInt()
    val read = delegate.read(position, cache, 0, readAheadLength)
    if (read <= 0) {
      cacheStart = -1L
      cacheSize = 0
      return read
    }

    cacheStart = position
    cacheSize = read
    val copied = minOf(length, read)
    cache.copyInto(
      destination = buffer,
      destinationOffset = offset,
      startIndex = 0,
      endIndex = copied,
    )
    return copied
  }

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    cacheStart = -1L
    cacheSize = 0
    delegate.close()
  }
}
