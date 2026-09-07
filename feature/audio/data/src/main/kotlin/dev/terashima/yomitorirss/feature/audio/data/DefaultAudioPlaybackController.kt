package dev.terashima.yomitorirss.feature.audio.data

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackController
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackState
import dev.terashima.yomitorirss.feature.audio.AudioPreparationStatus
import dev.terashima.yomitorirss.feature.audio.AudioQueueItem
import dev.terashima.yomitorirss.feature.audio.normalizeAudioQueue
import dev.terashima.yomitorirss.feature.summary.SummaryReader
import dev.terashima.yomitorirss.feature.summary.SummaryRequestResult
import dev.terashima.yomitorirss.feature.summary.SummaryRequester
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DefaultAudioPlaybackController(
  context: Context,
  private val summaryReader: SummaryReader,
  private val summaryRequester: SummaryRequester,
) : AudioPlaybackController {
  private val applicationContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val mutableState = MutableStateFlow(AudioPlaybackState())
  override val state: StateFlow<AudioPlaybackState> = mutableState.asStateFlow()

  private val synthesisWaiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
  private var textToSpeech: TextToSpeech? = null
  private var mediaController: MediaController? = null
  private var prepareJob: Job? = null
  private var positionJob: Job? = null

  override fun play(items: List<AudioQueueItem>) {
    val queue = normalizeAudioQueue(items)
    if (queue.isEmpty()) return

    prepareJob?.cancel()
    prepareJob = scope.launch {
      mutableState.value = AudioPlaybackState(
        items = queue,
        preparationStatus = AudioPreparationStatus.PREPARING,
        totalCount = queue.size,
        message = "要約と音声を準備しています",
      )

      runCatching {
        prepareAndPlay(queue)
      }.onFailure { error ->
        mutableState.update {
          it.copy(
            isPlaying = false,
            preparationStatus = AudioPreparationStatus.FAILED,
            message = error.message ?: "音声の準備に失敗しました",
          )
        }
      }
    }
  }

  override fun togglePlayPause() {
    mediaController?.let { controller ->
      if (controller.isPlaying) controller.pause() else controller.play()
      syncPlayerState(controller)
    }
  }

  override fun skipNext() {
    mediaController?.let { controller ->
      if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
      syncPlayerState(controller)
    }
  }

  override fun skipPrevious() {
    mediaController?.let { controller ->
      if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
      else controller.seekTo(0L)
      syncPlayerState(controller)
    }
  }

  override fun seekBy(deltaMs: Long) {
    mediaController?.let { controller ->
      val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L }
      val destination = (controller.currentPosition + deltaMs).coerceAtLeast(0L).let { position ->
        duration?.let { max -> position.coerceAtMost(max) } ?: position
      }
      controller.seekTo(destination)
      syncPlayerState(controller)
    }
  }

  override fun setPlaybackSpeed(speed: Float) {
    val normalized = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
    mediaController?.setPlaybackSpeed(normalized)
    mutableState.update { it.copy(playbackSpeed = normalized) }
  }

  override fun stop() {
    prepareJob?.cancel()
    prepareJob = null
    positionJob?.cancel()
    positionJob = null
    textToSpeech?.stop()
    mediaController?.let { controller ->
      controller.stop()
      controller.clearMediaItems()
    }
    mutableState.value = AudioPlaybackState()
  }

  private suspend fun prepareAndPlay(queue: List<AudioQueueItem>) {
    val summaries = resolveSummaries(queue)
    val prepared = mutableListOf<Pair<AudioQueueItem, File>>()

    queue.forEach { item ->
      val summary = summaries[item.contentId] ?: return@forEach
      val file = synthesize(item, summary)
      prepared += item to file
      mutableState.update {
        it.copy(
          preparedCount = prepared.size,
          message = "音声を準備しています (${prepared.size}/${queue.size})",
        )
      }
    }

    if (prepared.isEmpty()) {
      error("再生できる要約がありません")
    }

    val controller = ensureMediaController()
    val playableItems = prepared.map { it.first }
    val mediaItems = prepared.map { (item, file) ->
      MediaItem.Builder()
        .setMediaId(item.contentId)
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(
          MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.source)
            .build(),
        )
        .build()
    }

    controller.setMediaItems(mediaItems)
    controller.prepare()
    controller.play()

    val skippedCount = queue.size - prepared.size
    mutableState.value = AudioPlaybackState(
      items = playableItems,
      currentIndex = if (playableItems.isEmpty()) -1 else 0,
      isPlaying = true,
      playbackSpeed = controller.playbackParameters.speed,
      preparationStatus = AudioPreparationStatus.READY,
      preparedCount = prepared.size,
      totalCount = queue.size,
      message = if (skippedCount > 0) "要約を取得できなかった${skippedCount}件を除いて再生します" else null,
    )
    syncPlayerState(controller)
    startPositionUpdates(controller)
  }

  private suspend fun resolveSummaries(queue: List<AudioQueueItem>): Map<String, String> {
    val summaries = linkedMapOf<String, String>()
    val pending = linkedSetOf<String>()

    queue.forEach { item ->
      val cached = summaryReader.findSummary(item.contentId)
      if (!cached.isNullOrBlank()) {
        summaries[item.contentId] = cached
      } else {
        when (val result = summaryRequester.request(item.contentId, forceRefresh = false)) {
          is SummaryRequestResult.Cached -> summaries[item.contentId] = result.summary
          is SummaryRequestResult.PreviousFailure -> Unit
          SummaryRequestResult.Processing,
          is SummaryRequestResult.Enqueued -> pending += item.contentId
        }
      }
    }

    if (pending.isEmpty()) return summaries

    val deadline = SystemClock.elapsedRealtime() + SUMMARY_WAIT_TIMEOUT_MS
    while (pending.isNotEmpty() && SystemClock.elapsedRealtime() < deadline && currentCoroutineContext().isActive) {
      delay(SUMMARY_POLL_INTERVAL_MS)
      pending.toList().forEach { contentId ->
        val summary = summaryReader.findSummary(contentId)
        if (!summary.isNullOrBlank()) {
          summaries[contentId] = summary
          pending.remove(contentId)
        }
      }
      mutableState.update {
        val readyCount = summaries.size
        it.copy(message = "要約を待っています ($readyCount/${queue.size})")
      }
    }

    return summaries
  }

  private suspend fun synthesize(item: AudioQueueItem, summary: String): File {
    val tts = ensureTextToSpeech()
    val cacheDirectory = File(applicationContext.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
    val cacheKey = sha256("${item.contentId}\u0000$summary")
    val output = File(cacheDirectory, "$cacheKey.wav")
    if (output.isFile && output.length() > 0L) return output

    val text = buildString {
      append(item.title.trim())
      append("。")
      append(summary.trim())
    }.take(TextToSpeech.getMaxSpeechInputLength())

    val utteranceId = UUID.randomUUID().toString()
    val completed = CompletableDeferred<Unit>()
    synthesisWaiters[utteranceId] = completed
    val queued = tts.synthesizeToFile(text, Bundle(), output, utteranceId)
    if (queued != TextToSpeech.SUCCESS) {
      synthesisWaiters.remove(utteranceId)
      error("音声合成を開始できませんでした")
    }

    try {
      completed.await()
    } catch (error: Throwable) {
      output.delete()
      throw error
    }
    if (!output.isFile || output.length() == 0L) error("音声ファイルを生成できませんでした")
    return output
  }

  private suspend fun ensureTextToSpeech(): TextToSpeech {
    textToSpeech?.let { return it }

    val initialized = CompletableDeferred<Int>()
    val tts = TextToSpeech(applicationContext) { status -> initialized.complete(status) }
    if (initialized.await() != TextToSpeech.SUCCESS) {
      tts.shutdown()
      error("端末の音声合成エンジンを初期化できませんでした")
    }

    if (tts.isLanguageAvailable(Locale.JAPANESE) >= TextToSpeech.LANG_AVAILABLE) {
      tts.language = Locale.JAPANESE
    }
    tts.setOnUtteranceProgressListener(
      object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = Unit

        override fun onDone(utteranceId: String) {
          synthesisWaiters.remove(utteranceId)?.complete(Unit)
        }

        @Deprecated("Deprecated in Android API")
        override fun onError(utteranceId: String) {
          synthesisWaiters.remove(utteranceId)
            ?.completeExceptionally(IllegalStateException("音声合成に失敗しました"))
        }

        override fun onError(utteranceId: String, errorCode: Int) {
          synthesisWaiters.remove(utteranceId)
            ?.completeExceptionally(IllegalStateException("音声合成に失敗しました ($errorCode)"))
        }
      },
    )
    textToSpeech = tts
    return tts
  }

  private suspend fun ensureMediaController(): MediaController {
    mediaController?.let { return it }

    val token = SessionToken(
      applicationContext,
      ComponentName(applicationContext, AudioPlaybackService::class.java),
    )
    val future = MediaController.Builder(applicationContext, token).buildAsync()
    val controller = suspendCancellableCoroutine<MediaController> { continuation ->
      future.addListener(
        {
          runCatching(future::get)
            .onSuccess { continuation.resume(it) }
            .onFailure { continuation.resumeWithException(it) }
        },
        ContextCompat.getMainExecutor(applicationContext),
      )
      continuation.invokeOnCancellation { future.cancel(true) }
    }
    controller.addListener(
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          syncPlayerState(controller)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          syncPlayerState(controller)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          syncPlayerState(controller)
        }
      },
    )
    mediaController = controller
    return controller
  }

  private fun startPositionUpdates(controller: MediaController) {
    positionJob?.cancel()
    positionJob = scope.launch {
      while (isActive && mutableState.value.preparationStatus != AudioPreparationStatus.IDLE) {
        syncPlayerState(controller)
        delay(POSITION_UPDATE_INTERVAL_MS)
      }
    }
  }

  private fun syncPlayerState(controller: MediaController) {
    val itemCount = controller.mediaItemCount
    val currentIndex = if (itemCount == 0) -1 else controller.currentMediaItemIndex.coerceIn(0, itemCount - 1)
    val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    mutableState.update {
      it.copy(
        currentIndex = currentIndex,
        isPlaying = controller.isPlaying,
        positionMs = controller.currentPosition.coerceAtLeast(0L),
        durationMs = duration,
        playbackSpeed = controller.playbackParameters.speed,
      )
    }
  }

  private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

  private companion object {
    const val CACHE_DIRECTORY = "summary-audio"
    const val SUMMARY_WAIT_TIMEOUT_MS = 10 * 60 * 1000L
    const val SUMMARY_POLL_INTERVAL_MS = 2_000L
    const val POSITION_UPDATE_INTERVAL_MS = 500L
    const val MIN_PLAYBACK_SPEED = 0.75f
    const val MAX_PLAYBACK_SPEED = 2.0f
  }
}
