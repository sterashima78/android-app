package dev.terashima.yomitorirss.feature.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoUiState(
  val items: List<VideoItem> = emptyList(),
  val extractorRules: List<WebVideoExtractorRule> = emptyList(),
  val loading: Boolean = true,
  val busy: Boolean = false,
  val busyMessage: String? = null,
  val message: String? = null,
)

data class VideoPlaybackSession(
  val item: VideoItem,
  val target: VideoPlaybackTarget,
  val isFullscreen: Boolean = false,
)

class VideoViewModel(
  private val repository: VideoRepository,
) : ViewModel() {
  private val mutableState = MutableStateFlow(VideoUiState())
  val state: StateFlow<VideoUiState> = mutableState.asStateFlow()

  private val mutablePlaybackSession = MutableStateFlow<VideoPlaybackSession?>(null)
  val playbackSession: StateFlow<VideoPlaybackSession?> = mutablePlaybackSession.asStateFlow()
  private var latestPlaybackPositionMs: Long = 0L

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      runCatching {
        val items = repository.items()
        val rules = repository.extractorRules()
        items to rules
      }.fold(
        onSuccess = { (items, rules) ->
          mutableState.value = mutableState.value.copy(
            items = items,
            extractorRules = rules,
            loading = false,
            busy = false,
            busyMessage = null,
            message = null,
          )
        },
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            loading = false,
            busy = false,
            busyMessage = null,
            message = error.message ?: "動画一覧を読み込めませんでした",
          )
        },
      )
    }
  }

  fun addWeb(url: String) = launchMutation(
    fallbackMessage = "Web動画を追加できませんでした",
    busyMessage = "Web動画を追加中…",
  ) {
    repository.addWeb(url)
  }

  fun refreshSmb() = launchMutation("SMB動画を同期できませんでした") {
    repository.refreshSmb()
  }

  fun remove(item: VideoItem) = launchMutation("動画を削除できませんでした") {
    repository.remove(item.id)
  }

  fun setCompleted(item: VideoItem, completed: Boolean) = launchMutation("視聴状態を更新できませんでした") {
    repository.setCompleted(item.id, completed)
  }

  fun openPlayback(item: VideoItem, target: VideoPlaybackTarget) {
    require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)
    latestPlaybackPositionMs = item.playbackState?.positionMs ?: 0L
    mutablePlaybackSession.value = VideoPlaybackSession(item, target)
  }

  fun setPlaybackFullscreen(isFullscreen: Boolean) {
    val session = mutablePlaybackSession.value ?: return
    mutablePlaybackSession.value = session.copy(isFullscreen = isFullscreen)
  }

  fun closePlayback() {
    mutablePlaybackSession.value = null
    latestPlaybackPositionMs = 0L
  }

  fun resumePositionMs(item: VideoItem): Long = if (mutablePlaybackSession.value?.item?.id == item.id) {
    latestPlaybackPositionMs
  } else {
    item.playbackState?.positionMs ?: 0L
  }

  fun savePlayback(item: VideoItem, positionMs: Long, durationMs: Long) {
    if (mutablePlaybackSession.value?.item?.id == item.id) {
      latestPlaybackPositionMs = positionMs.coerceAtLeast(0L)
    }
    viewModelScope.launch {
      runCatching {
        repository.updatePlayback(item.id, positionMs, durationMs)
      }.onFailure { error ->
        mutableState.value = mutableState.value.copy(
          message = error.message ?: "再生位置を保存できませんでした",
        )
      }
    }
  }

  fun saveExtractorRule(rule: WebVideoExtractorRule) = launchMutation("抽出ルールを保存できませんでした") {
    repository.saveExtractorRule(rule)
  }

  fun deleteExtractorRule(id: String) = launchMutation("抽出ルールを削除できませんでした") {
    repository.deleteExtractorRule(id)
  }

  fun dismissMessage() {
    mutableState.value = mutableState.value.copy(message = null)
  }

  private fun launchMutation(
    fallbackMessage: String,
    busyMessage: String? = null,
    block: suspend () -> Unit,
  ) {
    if (mutableState.value.busy) return
    mutableState.value = mutableState.value.copy(
      busy = true,
      busyMessage = busyMessage,
      message = null,
    )
    viewModelScope.launch {
      runCatching { block() }.fold(
        onSuccess = {
          runCatching {
            repository.items() to repository.extractorRules()
          }.fold(
            onSuccess = { (items, rules) ->
              mutableState.value = mutableState.value.copy(
                items = items,
                extractorRules = rules,
                loading = false,
                busy = false,
                busyMessage = null,
              )
            },
            onFailure = { error ->
              mutableState.value = mutableState.value.copy(
                busy = false,
                busyMessage = null,
                message = error.message ?: fallbackMessage,
              )
            },
          )
        },
        onFailure = { error ->
          mutableState.value = mutableState.value.copy(
            busy = false,
            busyMessage = null,
            message = error.message ?: fallbackMessage,
          )
        },
      )
    }
  }

  class Factory(
    private val repository: VideoRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(VideoViewModel::class.java))
      return VideoViewModel(repository) as T
    }
  }
}
