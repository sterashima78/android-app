package dev.terashima.yomitorirss.feature.rss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
  val initialized: Boolean = false,
  val feeds: List<Feed> = emptyList(),
  val folders: List<FeedFolder> = emptyList(),
  val refreshing: Boolean = false,
  val refreshProgress: String? = null,
  val message: String? = null,
  val feedCandidates: List<FeedCandidate> = emptyList(),
  val importCompleted: Boolean = false,
  val feedAdded: Boolean = false,
)

class FeedViewModel(
  private val repository: FeedRepository,
  private val refreshFeeds: RefreshFeedsUseCase,
  private val imports: FeedImportRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
  private val feedSelector: (Feed) -> Boolean = { true },
  private val canAddInput: (String) -> Boolean = { true },
) : ViewModel() {
  private val _state = MutableStateFlow(FeedUiState())
  val state: StateFlow<FeedUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      repository.changes.collect { reload() }
    }
    viewModelScope.launch(Dispatchers.IO) {
      reload()
      if (_state.value.feeds.isNotEmpty()) refresh(showCompletionMessage = false)
    }
  }

  fun refresh(showCompletionMessage: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) { refreshInternal(showCompletionMessage) }
  }

  private suspend fun refreshInternal(showCompletionMessage: Boolean) {
    if (_state.value.refreshing) return
    runCatching {
      val feeds = repository.listFeeds().filter(feedSelector)
      _state.update {
        it.copy(
          refreshing = true,
          refreshProgress = if (feeds.isEmpty()) null else "0 / ${feeds.size}",
        )
      }
      val result = refreshFeeds(feeds) { completed, total ->
        _state.update { it.copy(refreshProgress = "$completed / $total") }
      }
      reload()
      _state.update {
        it.copy(
          refreshing = false,
          refreshProgress = null,
          message = when {
            !showCompletionMessage -> it.message
            result.total == 0 -> "登録済みフィードはありません"
            result.failures == 0 -> "フィードを更新しました"
            result.failures == result.total -> "すべてのフィードの更新に失敗しました"
            else -> "${result.total - result.failures}件を更新し、${result.failures}件で失敗しました"
          },
        )
      }
    }.onFailure { error ->
      _state.update {
        it.copy(
          initialized = true,
          refreshing = false,
          refreshProgress = null,
          message = "フィードを更新できませんでした: ${error.userMessage()}",
        )
      }
    }
  }

  fun inspectAndAddFeed(input: String) {
    if (!canAddInput(input)) {
      _state.update { it.copy(message = "このURLはRSSではなく専用画面から追加してください") }
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.inspect(input) }
        .onSuccess { inspection ->
          inspection.directFeedUrl?.let { addFeedUrl(it) }
            ?: _state.update { it.copy(feedCandidates = inspection.candidates) }
        }
        .onFailure(::showError)
    }
  }

  fun addFeedCandidate(candidate: FeedCandidate) {
    _state.update { it.copy(feedCandidates = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) { addFeedUrl(candidate.url) }
  }

  fun dismissFeedCandidates() {
    _state.update { it.copy(feedCandidates = emptyList()) }
  }

  private suspend fun addFeedUrl(
    url: String,
    successMessage: String = "フィードを追加しました",
    markFeedAdded: Boolean = true,
    markExistingArticlesRead: Boolean = false,
  ) {
    if (!canAddInput(url)) {
      _state.update { it.copy(message = "このフィードはRSSではなく専用画面から追加してください") }
      return
    }
    runCatching { repository.addFeed(url, markExistingArticlesRead) }
      .onSuccess {
        backupChangeScheduler.scheduleAfterChange()
        _state.update {
          it.copy(
            message = successMessage,
            feedAdded = it.feedAdded || markFeedAdded,
          )
        }
      }
      .onFailure(::showError)
  }

  fun consumeFeedAdded() {
    _state.update { it.copy(feedAdded = false) }
  }

  fun deleteFeed(feed: Feed) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.deleteFeed(feed.id) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "${feed.title}を削除しました") }
        }
        .onFailure(::showError)
    }
  }

  fun createFolder(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.createFolder(name) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "フォルダを作成しました") }
        }
        .onFailure(::showError)
    }
  }

  fun renameFolder(folder: FeedFolder, name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.renameFolder(folder.id, name) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "フォルダ名を変更しました") }
        }
        .onFailure(::showError)
    }
  }

  fun deleteFolder(folder: FeedFolder) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.deleteFolder(folder.id) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "${folder.name}を削除しました") }
        }
        .onFailure(::showError)
    }
  }

  fun moveFeedToFolder(feed: Feed, folderId: String?) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.moveFeedToFolder(feed.id, folderId) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          _state.update { it.copy(message = "${feed.title}を移動しました") }
        }
        .onFailure(::showError)
    }
  }

  fun importOpml(documentUri: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { imports.importFeedOpml(documentUri) }
        .onSuccess { result ->
          if (result.added > 0) {
            backupChangeScheduler.scheduleAfterChange()
            refreshInternal(showCompletionMessage = false)
          }
          _state.update {
            it.copy(
              message = "${result.added}件のフィードをインポートしました（重複 ${result.duplicates}件、スキップ ${result.skipped}件）",
              importCompleted = true,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(message = "OPMLをインポートできませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun consumeImportCompleted() {
    _state.update { it.copy(importCompleted = false) }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private suspend fun reload() {
    runCatching {
      repository.listFeeds().filter(feedSelector) to repository.listFolders()
    }.onSuccess { (feeds, folders) ->
      _state.update { it.copy(initialized = true, feeds = feeds, folders = folders) }
    }.onFailure { error ->
      _state.update {
        it.copy(
          initialized = true,
          message = "フィードを読み込めませんでした: ${error.userMessage()}",
        )
      }
    }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(message = error.userMessage()) }
  }

  class Factory(
    private val repository: FeedRepository,
    private val refreshFeeds: RefreshFeedsUseCase,
    private val imports: FeedImportRepository,
    private val backupChangeScheduler: BackupChangeScheduler,
    private val feedSelector: (Feed) -> Boolean = { true },
    private val canAddInput: (String) -> Boolean = { true },
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(FeedViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return FeedViewModel(
        repository,
        refreshFeeds,
        imports,
        backupChangeScheduler,
        feedSelector,
        canAddInput,
      ) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
