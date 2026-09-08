package dev.terashima.yomitorirss.feature.video.data

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.VideoThumbnailResolver
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class DefaultVideoThumbnailResolver(
  private val byteSourceFactory: VideoByteSourceFactory,
  cacheDirectory: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : VideoThumbnailResolver {
  private val thumbnailDirectory = File(cacheDirectory, "video-thumbnails")
  private val generationSemaphore = Semaphore(MAX_CONCURRENT_GENERATIONS)

  override suspend fun resolve(item: VideoItem): String? {
    if (item.source != VideoSource.SMB) return item.thumbnailUrl
    val target = File(thumbnailDirectory, thumbnailCacheFileName(item))
    return withContext(ioDispatcher) {
      cachedThumbnail(target)?.let { return@withContext it }
      generationSemaphore.withPermit {
        cachedThumbnail(target)?.let { return@withPermit it }
        try {
          generateThumbnail(item, target)
        } catch (error: CancellationException) {
          throw error
        } catch (_: Throwable) {
          null
        }
      }
    }
  }

  private fun cachedThumbnail(target: File): String? {
    if (!target.isFile || target.length() <= 0L) return null
    target.setLastModified(System.currentTimeMillis())
    return Uri.fromFile(target).toString()
  }

  private fun generateThumbnail(item: VideoItem, target: File): String {
    check(thumbnailDirectory.isDirectory || thumbnailDirectory.mkdirs()) {
      "動画サムネイルのキャッシュ領域を作成できません"
    }
    thumbnailDirectory.listFiles()
      ?.filter { file -> file.name.startsWith("${item.id}-") && file != target }
      ?.forEach { file -> file.delete() }

    val temporary = File(thumbnailDirectory, ".${target.name}.${System.nanoTime()}.tmp")
    val dataSource = VideoMediaDataSource(byteSourceFactory.open(item.sourceId))
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(dataSource)
      val bitmap = retriever.thumbnailFrame() ?: error("動画からサムネイルを取得できません")
      try {
        FileOutputStream(temporary).use { output ->
          check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
            "動画サムネイルを書き出せません"
          }
        }
      } finally {
        bitmap.recycle()
      }
      if (!temporary.renameTo(target)) {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
      }
      target.setLastModified(System.currentTimeMillis())
      pruneCache()
      return Uri.fromFile(target).toString()
    } finally {
      retriever.release()
      dataSource.close()
      temporary.delete()
    }
  }

  private fun pruneCache() {
    val files = thumbnailDirectory.listFiles()
      ?.asSequence()
      ?.filter { file -> file.isFile && !file.name.startsWith('.') }
      ?.sortedByDescending(File::lastModified)
      ?.toList()
      ?: return
    var keptFiles = 0
    var keptBytes = 0L
    files.forEach { file ->
      val size = file.length().coerceAtLeast(0L)
      val canKeep = keptFiles < MAX_CACHE_FILES && keptBytes + size <= MAX_CACHE_BYTES
      if (canKeep) {
        keptFiles += 1
        keptBytes += size
      } else {
        file.delete()
      }
    }
  }

  private fun MediaMetadataRetriever.thumbnailFrame(): Bitmap? {
    val durationMs = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    val timeUs = ((durationMs?.div(10)?.coerceIn(MIN_FRAME_TIME_MS, MAX_FRAME_TIME_MS))
      ?: MIN_FRAME_TIME_MS) * 1_000L
    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
    val dimensions = if (width != null && width > 0 && height != null && height > 0) {
      val scale = minOf(1.0, MAX_THUMBNAIL_EDGE.toDouble() / maxOf(width, height).toDouble())
      (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
    } else {
      DEFAULT_THUMBNAIL_WIDTH to DEFAULT_THUMBNAIL_HEIGHT
    }
    return getScaledFrameAtTime(
      timeUs,
      MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
      dimensions.first,
      dimensions.second,
    ) ?: getScaledFrameAtTime(
      0L,
      MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
      dimensions.first,
      dimensions.second,
    )
  }

  private class VideoMediaDataSource(
    private val source: VideoByteSource,
  ) : MediaDataSource() {
    private var closed = false

    override fun readAt(
      position: Long,
      buffer: ByteArray,
      offset: Int,
      size: Int,
    ): Int = if (closed) {
      -1
    } else {
      source.read(position, buffer, offset, size)
    }

    override fun getSize(): Long = source.length

    override fun close() {
      if (closed) return
      closed = true
      source.close()
    }
  }

  private companion object {
    const val JPEG_QUALITY = 82
    const val MAX_THUMBNAIL_EDGE = 640
    const val DEFAULT_THUMBNAIL_WIDTH = 640
    const val DEFAULT_THUMBNAIL_HEIGHT = 360
    const val MIN_FRAME_TIME_MS = 1_000L
    const val MAX_FRAME_TIME_MS = 30_000L
    const val MAX_CONCURRENT_GENERATIONS = 2
    const val MAX_CACHE_FILES = 2_000
    const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

    fun thumbnailCacheFileName(item: VideoItem): String =
      "${item.id}-${item.sizeBytes ?: 0L}.jpg"
  }
}
