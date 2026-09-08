package dev.terashima.yomitorirss.feature.video

enum class VideoSource {
  SMB,
  WEB,
  SERVICE,
}

data class VideoItem(
  val id: String,
  val source: VideoSource,
  val sourceId: String,
  val title: String,
  val pageUrl: String? = null,
  val thumbnailUrl: String? = null,
  val durationMs: Long? = null,
  val sizeBytes: Long? = null,
  val mimeType: String? = null,
  val updatedAtEpochMillis: Long,
  val playbackState: VideoPlaybackState? = null,
  val savedState: VideoSavedState? = null,
)

data class VideoSmbSource(
  val id: String,
  val serverId: String,
  val share: String,
  val rootPath: String = "",
  val updatedAtEpochMillis: Long = 0L,
)

data class VideoPlaybackState(
  val positionMs: Long,
  val durationMs: Long,
  val lastPlayedAtEpochMillis: Long,
  val completed: Boolean,
)

data class VideoSavedState(
  val folderId: String? = null,
  val savedAtEpochMillis: Long,
)

data class VideoFolder(
  val id: String,
  val name: String,
  val createdAtEpochMillis: Long = 0L,
  val updatedAtEpochMillis: Long = 0L,
)

data class WebVideoExtractorRule(
  val id: String,
  val urlPattern: String,
  val titleExtractorCode: String? = null,
  val thumbnailExtractorCode: String? = null,
  val playbackExtractorCode: String? = null,
  val timeoutSeconds: Int = 15,
  val updatedAtEpochMillis: Long,
  val shareCookiesForPlayback: Boolean = false,
)

/**
 * Transient capability for resolving the Cookie request header for Web stream playback.
 *
 * Returned values may contain credentials. Callers must not persist or log them.
 */
fun interface VideoPlaybackCookieProvider {
  fun cookieHeaderFor(url: String): String?
}

data class WebVideoExtractionResult(
  val title: String? = null,
  val thumbnailUrl: String? = null,
  val streamUrl: String? = null,
  val mimeType: String? = null,
  val referrerUrl: String? = null,
  val cookieProvider: VideoPlaybackCookieProvider? = null,
)

sealed interface VideoPlaybackTarget {
  data class Stream(
    val url: String,
    val mimeType: String? = null,
    val referrerUrl: String? = null,
    val cookieProvider: VideoPlaybackCookieProvider? = null,
  ) : VideoPlaybackTarget

  data class Smb(
    val sourceId: String,
    val length: Long,
    val mimeType: String? = null,
  ) : VideoPlaybackTarget

  data class WebPage(val url: String) : VideoPlaybackTarget
}

interface VideoRepository {
  suspend fun items(): List<VideoItem>

  suspend fun addWeb(url: String): VideoItem

  suspend fun remove(id: String)

  suspend fun refreshSmb(): Int

  fun smbSources(): List<VideoSmbSource>

  fun saveSmbSource(source: VideoSmbSource): VideoSmbSource

  fun deleteSmbSource(id: String)

  fun folders(): List<VideoFolder>

  fun saveFolder(folder: VideoFolder): VideoFolder

  fun deleteFolder(id: String)

  fun saveVideo(videoId: String, folderId: String? = null)

  fun removeSavedVideo(videoId: String)

  suspend fun updatePlayback(
    videoId: String,
    positionMs: Long,
    durationMs: Long,
    completed: Boolean? = null,
  )

  suspend fun setCompleted(videoId: String, completed: Boolean)

  fun extractorRules(): List<WebVideoExtractorRule>

  fun saveExtractorRule(rule: WebVideoExtractorRule): WebVideoExtractorRule

  fun deleteExtractorRule(id: String)
}

interface VideoPlaybackResolver {
  suspend fun resolve(item: VideoItem): VideoPlaybackTarget
}

interface VideoThumbnailResolver {
  suspend fun resolve(item: VideoItem): String?
}

interface VideoByteSource : AutoCloseable {
  val length: Long

  fun read(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int,
  ): Int
}

interface VideoByteSourceFactory {
  fun open(sourceId: String): VideoByteSource
}
