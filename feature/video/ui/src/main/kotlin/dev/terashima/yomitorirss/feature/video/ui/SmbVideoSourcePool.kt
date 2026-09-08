package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory

/** Keeps SMB byte sources open across Media3 DataSource reopen cycles within one player lifetime. */
internal class SmbVideoSourcePool(
  private val byteSourceFactory: VideoByteSourceFactory,
) : AutoCloseable {
  private val entries = mutableMapOf<String, Entry>()
  private var closed = false

  fun acquire(sourceId: String): Lease = synchronized(this) {
    check(!closed) { "SMB動画のsource poolは既に閉じられています" }
    val entry = entries[sourceId] ?: Entry(byteSourceFactory.open(sourceId)).also {
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

  internal class Lease private constructor(
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

    internal companion object {
      operator fun invoke(
        pool: SmbVideoSourcePool,
        sourceId: String,
        entry: Entry,
      ): Lease = Lease(pool, sourceId, entry)
    }
  }

  private class Entry(
    val source: VideoByteSource,
    var activeLeases: Int = 0,
    var retired: Boolean = false,
  )
}
