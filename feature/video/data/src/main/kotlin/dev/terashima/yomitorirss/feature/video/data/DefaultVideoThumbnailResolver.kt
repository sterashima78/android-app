package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.inspector.frame.FrameExtractor
import com.google.common.util.concurrent.ListenableFuture
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.VideoThumbnailResolver
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class DefaultVideoThumbnailResolver(
  context: Context,
  private val byteSourceFactory: VideoByteSourceFactory,
  cacheDirectory: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val frameDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : VideoThumbnailResolver {
  private val applicationContext = context.applicationContext
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

  private suspend fun generateThumbnail(item: VideoItem, target: File): String {
    check(thumbnailDirectory.isDirectory || thumbnailDirectory.mkdirs()) {
      "動画サムネイルのキャッシュ領域を作成できません"
    }
    thumbnailDirectory.listFiles()
      ?.filter { file -> file.name.startsWith("${item.id}-") && file != target }
      ?.forEach { file -> file.delete() }

    val temporary = File(thumbnailDirectory, ".${target.name}.${System.nanoTime()}.tmp")
    val source = byteSourceFactory.open(item.sourceId)
    try {
      val bitmap = extractThumbnailFrame(source).scaleToThumbnailBounds()
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
      source.close()
      temporary.delete()
    }
  }

  private suspend fun extractThumbnailFrame(source: VideoByteSource): Bitmap = withContext(frameDispatcher) {
    val dataSourceFactory = DataSource.Factory { ThumbnailVideoDataSource(source) }
    val extractor = FrameExtractor.Builder(
      applicationContext,
      MediaItem.fromUri(THUMBNAIL_MEDIA_URI),
    )
      .setMediaSourceFactory(ProgressiveMediaSource.Factory(dataSourceFactory))
      .setSeekParameters(SeekParameters.CLOSEST_SYNC)
      .setEffects(listOf(Presentation.createForHeight(EXTRACTION_HEIGHT)))
      .build()
    try {
      try {
        extractor.getFrame(PRIMARY_FRAME_TIME_MS).awaitResult().bitmap
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        extractor.getFrame(0L).awaitResult().bitmap
      }
    } finally {
      extractor.close()
    }
  }

  private fun Bitmap.scaleToThumbnailBounds(): Bitmap {
    val longestEdge = maxOf(width, height)
    if (longestEdge <= MAX_THUMBNAIL_EDGE) return this
    val scale = MAX_THUMBNAIL_EDGE.toDouble() / longestEdge.toDouble()
    val scaled = Bitmap.createScaledBitmap(
      this,
      (width * scale).roundToInt().coerceAtLeast(1),
      (height * scale).roundToInt().coerceAtLeast(1),
      true,
    )
    if (scaled !== this) recycle()
    return scaled
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

  private companion object {
    const val JPEG_QUALITY = 82
    const val MAX_THUMBNAIL_EDGE = 640
    const val EXTRACTION_HEIGHT = 360
    const val PRIMARY_FRAME_TIME_MS = 1_000L
    const val MAX_CONCURRENT_GENERATIONS = 2
    const val MAX_CACHE_FILES = 2_000
    const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    val THUMBNAIL_MEDIA_URI: Uri = Uri.parse("mosaic-video://thumbnail/video")

    fun thumbnailCacheFileName(item: VideoItem): String =
      "${item.id}-${item.sizeBytes ?: 0L}.jpg"
  }
}

internal class ThumbnailVideoDataSource(
  private val source: VideoByteSource,
) : BaseDataSource(true) {
  private var uri: Uri? = null
  private var position = 0L
  private var bytesRemaining = 0L
  private var opened = false

  override fun open(dataSpec: DataSpec): Long {
    transferInitializing(dataSpec)
    position = dataSpec.position
    require(position <= source.length) { "SMB動画のサムネイル読込位置がファイル範囲外です" }
    val available = source.length - position
    bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
      available
    } else {
      minOf(dataSpec.length, available)
    }
    uri = dataSpec.uri
    opened = true
    transferStarted(dataSpec)
    return bytesRemaining
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val requested = minOf(length.toLong(), bytesRemaining).toInt()
    val read = synchronized(source) {
      source.read(position, buffer, offset, requested)
    }
    if (read <= 0) return C.RESULT_END_OF_INPUT
    position += read
    bytesRemaining -= read
    bytesTransferred(read)
    return read
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    uri = null
    position = 0L
    bytesRemaining = 0L
    if (opened) {
      opened = false
      transferEnded()
    }
  }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel(true) }
  addListener(
    {
      if (!continuation.isActive) return@addListener
      try {
        continuation.resumeWith(Result.success(get()))
      } catch (error: Throwable) {
        val cause = (error as? ExecutionException)?.cause ?: error
        continuation.resumeWith(Result.failure(cause))
      }
    },
    DIRECT_EXECUTOR,
  )
}

private val DIRECT_EXECUTOR = Executor { command -> command.run() }
