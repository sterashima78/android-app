package dev.terashima.yomitorirss.feature.integrated.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.video.VideoProviderRepository
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class IntegratedVideoProviderState(
  val initialized: Boolean = false,
  val unread: List<VideoProviderVideo> = emptyList(),
  val watchLater: List<VideoProviderVideo> = emptyList(),
  val history: List<VideoProviderVideo> = emptyList(),
  val refreshing: Boolean = false,
  val message: String? = null,
)

internal class IntegratedVideoProviderViewModel(
  private val providers: VideoProviderRepository,
  private val videos: VideoRepository,
) : ViewModel() {
  private val mutableState = MutableStateFlow(IntegratedVideoProviderState())
  val state: StateFlow<IntegratedVideoProviderState> = mutableState.asStateFlow()

  init {
    reload()
  }

  fun dismissMessage() {
    mutableState.value = mutableState.value.copy(message = null)
  }

  fun refresh() {
    if (mutableState.value.refreshing) return
    mutableState.value = mutableState.value.copy(refreshing = true, message = null)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        youtubeProviderIds().forEach { providerId -> providers.refreshProviders(providerId) }
        mutableState.value = snapshot().copy(message = "購読動画を更新しました")
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        mutableState.value = snapshot().copy(message = error.userMessage())
      }
    }
  }

  fun markRead(item: VideoProviderVideo) = mutate("既読状態を更新できませんでした") {
    providers.markRead(item.video.id)
  }

  fun markUnread(item: VideoProviderVideo) = mutate("未読状態を更新できませんでした") {
    providers.markUnread(item.video.id)
  }

  fun saveAndRead(item: VideoProviderVideo) = mutate("動画を保存できませんでした") {
    videos.saveVideo(item.video.id, folderId = null)
    providers.markRead(item.video.id)
  }

  fun toggleWatchLater(item: VideoProviderVideo) = mutate("あとで見る状態を更新できませんでした") {
    providers.setWatchLater(item.video.id, !item.isWatchLater)
  }

  private fun reload() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        mutableState.value = snapshot()
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        mutableState.value = mutableState.value.copy(
          initialized = true,
          refreshing = false,
          message = error.userMessage(),
        )
      }
    }
  }

  private fun mutate(
    fallbackMessage: String,
    block: suspend () -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        block()
        mutableState.value = snapshot()
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        mutableState.value = snapshot().copy(message = error.userMessage().ifBlank { fallbackMessage })
      }
    }
  }

  private fun snapshot(): IntegratedVideoProviderState {
    val providerIds = youtubeProviderIds()
    return IntegratedVideoProviderState(
      initialized = true,
      unread = providers.unreadVideos().filter { it.providerId in providerIds },
      watchLater = providers.watchLaterVideos().filter { it.providerId in providerIds },
      history = providers.historyVideos().filter { it.providerId in providerIds },
      refreshing = false,
      message = null,
    )
  }

  private fun youtubeProviderIds(): Set<String> = providers.providers()
    .asSequence()
    .filter { it.type == VideoProviderType.YOUTUBE }
    .mapTo(linkedSetOf()) { it.id }

  class Factory(
    private val providers: VideoProviderRepository,
    private val videos: VideoRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(IntegratedVideoProviderViewModel::class.java))
      return IntegratedVideoProviderViewModel(providers, videos) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
