package dev.terashima.yomitorirss.feature.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_KIND
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class YouTubeTab(val label: String) {
  UNREAD("未読"),
  WATCH_LATER("あとで見る"),
  SAVED("保存済み"),
  SUBSCRIPTIONS("購読管理"),
}

data class YouTubeUiState(
  val initialized: Boolean = false,
  val selectedTab: YouTubeTab = YouTubeTab.UNREAD,
  val unread: List<YouTubeVideo> = emptyList(),
  val history: List<YouTubeVideo> = emptyList(),
  val watchLater: List<YouTubeVideo> = emptyList(),
  val saved: List<YouTubeVideo> = emptyList(),
  val channels: List<YouTubeChannel> = emptyList(),
  val refreshing: Boolean = false,
  val message: String? = null,
)

class YouTubeViewModel(
  private val repository: YouTubeRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(YouTubeUiState())
  val state: StateFlow<YouTubeUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) { reload() }
  }

  fun selectTab(tab: YouTubeTab) {
    _state.update { it.copy(selectedTab = tab) }
    if (tab == YouTubeTab.SAVED) {
      viewModelScope.launch(Dispatchers.IO) { reloadSavedVideos() }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  fun refresh() {
    if (_state.value.refreshing) return
    _state.update { it.copy(refreshing = true) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.refresh() }
        .onSuccess {
          reload()
          _state.update { it.copy(refreshing = false, message = "YouTubeを更新しました") }
        }
        .onFailure { error ->
          reload()
          _state.update {
            it.copy(
              refreshing = false,
              message = "YouTubeを更新できませんでした: ${error.userMessage()}",
            )
          }
        }
    }
  }

  fun subscribe(channelUrl: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.subscribe(channelUrl) }
        .onSuccess { channel ->
          reload()
          _state.update {
            it.copy(
              selectedTab = YouTubeTab.SUBSCRIPTIONS,
              message = "${channel.title}を購読しました",
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(message = error.userMessage()) }
        }
    }
  }

  fun unsubscribe(channel: YouTubeChannel) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.unsubscribe(channel.id) }
        .onSuccess {
          reload()
          _state.update { it.copy(message = "${channel.title}の購読を解除しました") }
        }
        .onFailure { error ->
          _state.update { it.copy(message = error.userMessage()) }
        }
    }
  }

  fun markRead(video: YouTubeVideo) {
    removeFromVideoLists(video.id)
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.markRead(video.id) }
        .onSuccess { reload() }
        .onFailure { error ->
          reload()
          _state.update { it.copy(message = "既読にできませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun markUnread(video: YouTubeVideo) {
    val unreadVideo = video.copy(isRead = false, isWatchLater = false)
    _state.update { state ->
      state.copy(
        history = state.history.filterNot { it.id == video.id },
        unread = (state.unread + unreadVideo)
          .distinctBy(YouTubeVideo::id)
          .sortedByDescending(YouTubeVideo::publishedAtEpochMillis),
      )
    }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.markUnread(video.id) }
        .onFailure { error ->
          reload()
          _state.update { it.copy(message = "未読に戻せませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun saveAndRead(video: YouTubeVideo) {
    removeFromVideoLists(video.id)
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val youtubeFolderId = bookmarkRepository.listFolders()
          .firstOrNull { it.systemKind == YOUTUBE_FOLDER_KIND }
          ?.id
          ?: error("YouTubeブックマークフォルダが見つかりません")
        bookmarkRepository.saveSharedArticleToFolder(
          url = video.url,
          title = video.title,
          sourceTitle = video.channelTitle,
          folderId = youtubeFolderId,
        )
        backupChangeScheduler.scheduleAfterChange()
        repository.markRead(video.id)
      }.onSuccess {
        reload()
      }.onFailure { error ->
        reload()
        _state.update { it.copy(message = "動画を保存できませんでした: ${error.userMessage()}") }
      }
    }
  }

  fun unsave(video: YouTubeVideo) {
    _state.update { state ->
      state.copy(saved = state.saved.filterNot { it.url == video.url })
    }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val bookmarked = bookmarkRepository.listSavedArticles(tagId = null, folderId = null)
          .firstOrNull { it.article.url == video.url }
          ?: error("保存済み動画が見つかりません")
        bookmarkRepository.unsaveArticle(bookmarked.article.id)
        backupChangeScheduler.scheduleAfterChange()
      }.onSuccess {
        _state.update { it.copy(message = "${video.title}の保存を解除しました") }
      }.onFailure { error ->
        reloadSavedVideos()
        _state.update { it.copy(message = "保存を解除できませんでした: ${error.userMessage()}") }
      }
    }
  }

  fun toggleWatchLater(video: YouTubeVideo) {
    val watchLater = !video.isWatchLater
    val updatedVideo = video.copy(isRead = false, isWatchLater = watchLater)
    _state.update { state ->
      state.copy(
        unread = if (watchLater) {
          state.unread.filterNot { it.id == video.id }
        } else {
          (state.unread + updatedVideo)
            .distinctBy(YouTubeVideo::id)
            .sortedByDescending(YouTubeVideo::publishedAtEpochMillis)
        },
        watchLater = if (watchLater) {
          (state.watchLater + updatedVideo)
            .distinctBy(YouTubeVideo::id)
            .sortedByDescending(YouTubeVideo::publishedAtEpochMillis)
        } else {
          state.watchLater.filterNot { it.id == video.id }
        },
      )
    }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.setWatchLater(video.id, watchLater) }
        .onFailure { error ->
          reload()
          _state.update { it.copy(message = "あとで見るを更新できませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun markAllRead() {
    val count = _state.value.unread.size
    if (count == 0) return
    _state.update { it.copy(unread = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.markAllRead() }
        .onSuccess {
          reload()
          _state.update { it.copy(message = "${count}件を既読にしました") }
        }
        .onFailure { error ->
          reload()
          _state.update { it.copy(message = "すべて既読にできませんでした: ${error.userMessage()}") }
        }
    }
  }

  private fun removeFromVideoLists(videoId: String) {
    _state.update { state ->
      state.copy(
        unread = state.unread.filterNot { it.id == videoId },
        watchLater = state.watchLater.filterNot { it.id == videoId },
      )
    }
  }

  private suspend fun reload() {
    runCatching {
      YouTubeSnapshot(
        unread = repository.listUnreadVideos(),
        history = repository.listHistoryVideos(),
        watchLater = repository.listWatchLaterVideos(),
        saved = loadSavedVideos(),
        channels = repository.listChannels(),
      )
    }.onSuccess { snapshot ->
      _state.update {
        it.copy(
          initialized = true,
          unread = snapshot.unread,
          history = snapshot.history,
          watchLater = snapshot.watchLater,
          saved = snapshot.saved,
          channels = snapshot.channels,
        )
      }
    }.onFailure { error ->
      _state.update {
        it.copy(
          initialized = true,
          message = "YouTubeを読み込めませんでした: ${error.userMessage()}",
        )
      }
    }
  }

  private suspend fun reloadSavedVideos() {
    runCatching { loadSavedVideos() }
      .onSuccess { saved -> _state.update { it.copy(saved = saved) } }
      .onFailure { error ->
        _state.update { it.copy(message = "保存済み動画を読み込めませんでした: ${error.userMessage()}") }
      }
  }

  private suspend fun loadSavedVideos(): List<YouTubeVideo> =
    bookmarkRepository.listSavedArticles(tagId = null, folderId = null)
      .mapNotNull { bookmarked ->
        val article = bookmarked.article
        val videoId = article.url.youtubeVideoId() ?: return@mapNotNull null
        YouTubeVideo(
          id = videoId,
          channelId = "",
          channelTitle = article.sourceTitle,
          title = article.title,
          url = article.url,
          publishedAtEpochMillis = bookmarked.savedAt.toEpochMillisOrZero(),
          isRead = true,
          isWatchLater = false,
        )
      }
      .distinctBy(YouTubeVideo::url)
      .sortedByDescending(YouTubeVideo::publishedAtEpochMillis)

  class Factory(
    private val repository: YouTubeRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val backupChangeScheduler: BackupChangeScheduler,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(YouTubeViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return YouTubeViewModel(repository, bookmarkRepository, backupChangeScheduler) as T
    }
  }
}

private data class YouTubeSnapshot(
  val unread: List<YouTubeVideo>,
  val history: List<YouTubeVideo>,
  val watchLater: List<YouTubeVideo>,
  val saved: List<YouTubeVideo>,
  val channels: List<YouTubeChannel>,
)

private fun String.youtubeVideoId(): String? =
  youtubeVideoIdRegex.find(this)?.groupValues?.get(1)

private fun String.toEpochMillisOrZero(): Long =
  runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

private val youtubeVideoIdRegex = Regex(
  pattern = "(?:youtube\\.com/watch\\?(?:[^#]*&)?v=|youtu\\.be/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})",
  option = RegexOption.IGNORE_CASE,
)

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
