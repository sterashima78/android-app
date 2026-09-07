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
)

data class VideoPlaybackState(
  val positionMs: Long,
  val durationMs: Long,
  val lastPlayedAtEpochMillis: Long,
  val completed: Boolean,
)

data class WebVideoExtractorRule(
  val id: String,
  val urlPattern: String,
  val titleExtractorCode: String? = null,
  val thumbnailExtractorCode: String? = null,
  val playbackExtractorCode: String? = null,
  val timeoutSeconds: Int = 15,
  val updatedAtEpochMillis: Long,
)

data class WebVideoExtractionResult(
  val title: String? = null,
  val thumbnailUrl: String? = null,
  val streamUrl: String? = null,
  val mimeType: String? = null,
)

sealed interface VideoPlaybackTarget {
  data class Stream(
    val url: String,
    val mimeType: String? = null,
    val referrerUrl: String? = null,
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
