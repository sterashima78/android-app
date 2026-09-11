package dev.terashima.yomitorirss.feature.audio

import kotlinx.coroutines.flow.StateFlow

data class AudioQueueItem(
  val contentId: String,
  val title: String,
  val source: String?,
  val speechText: String? = null,
)

fun normalizeAudioQueue(items: List<AudioQueueItem>): List<AudioQueueItem> =
  items.distinctBy(AudioQueueItem::contentId)

enum class AudioPreparationStatus {
  IDLE,
  PREPARING,
  READY,
  FAILED,
}

data class AudioPlaybackState(
  val items: List<AudioQueueItem> = emptyList(),
  val currentIndex: Int = -1,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val playbackSpeed: Float = 1f,
  val preparationStatus: AudioPreparationStatus = AudioPreparationStatus.IDLE,
  val preparedCount: Int = 0,
  val totalCount: Int = 0,
  val message: String? = null,
) {
  val currentItem: AudioQueueItem?
    get() = items.getOrNull(currentIndex)
}

interface AudioPlaybackController {
  val state: StateFlow<AudioPlaybackState>

  fun play(items: List<AudioQueueItem>)

  fun togglePlayPause()

  fun skipNext()

  fun skipPrevious()

  fun seekBy(deltaMs: Long)

  fun setPlaybackSpeed(speed: Float)

  fun stop()
}
