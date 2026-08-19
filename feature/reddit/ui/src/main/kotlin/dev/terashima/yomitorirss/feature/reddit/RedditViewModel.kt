package dev.terashima.yomitorirss.feature.reddit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RedditUiState(
  val initialized: Boolean = false,
  val unread: List<Article> = emptyList(),
  val history: List<Article> = emptyList(),
  val readLater: List<BookmarkedArticle> = emptyList(),
  val subscriptions: List<RedditSubscription> = emptyList(),
  val hiddenArticleIds: Set<String> = emptySet(),
  val refreshing: Boolean = false,
  val refreshProgress: String? = null,
  val message: String? = null,
)

class RedditViewModel(
  private val redditRepository: RedditRepository,
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(RedditUiState())
  val state: StateFlow<RedditUiState> = _state.asStateFlow()
  private val reloadMutex = Mutex()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      reload()
      if (_state.value.subscriptions.isNotEmpty()) refreshInternal(showCompletionMessage = false)
    }
    viewModelScope.launch(Dispatchers.IO) {
      articleRepository.changes.collect { reload() }
    }
    viewModelScope.launch(Dispatchers.IO) {
      bookmarkRepository.changes.collect { reload() }
    }
    viewModelScope.launch(Dispatchers.IO) {
      redditRepository.changes.collect { reload() }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      refreshInternal(showCompletionMessage = true)
    }
  }

  private suspend fun refreshInternal(showCompletionMessage: Boolean) {
    if (_state.value.refreshing) return
    _state.update { it.copy(refreshing = true, refreshProgress = null) }
    runCatching {
      redditRepository.refreshAll { completed, total ->
        _state.update { state -> state.copy(refreshProgress = "$completed / $total") }
      }
    }.onSuccess { result ->
      reload()
      _state.update {
        it.copy(
          refreshing = false,
          refreshProgress = null,
          message = when {
            !showCompletionMessage -> it.message
            result.total == 0 -> "Redditの購読はありません"
            result.failures == 0 -> "Redditを更新しました"
            result.failures == result.total -> "Redditの更新に失敗しました"
            else -> "${result.total - result.failures}件を更新し、${result.failures}件で失敗しました"
          },
        )
      }
    }.onFailure { error ->
      _state.update {
        it.copy(
          refreshing = false,
          refreshProgress = null,
          message = if (showCompletionMessage) {
            "Redditを更新できませんでした: ${error.userMessage()}"
          } else {
            it.message
          },
        )
      }
    }
  }

  fun addCommunity(input: String) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { redditRepository.addCommunity(input) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          reload()
          _state.update { it.copy(message = "Redditコミュニティを購読しました") }
        }
        .onFailure(::showError)
    }
  }

  fun deleteSubscription(subscription: RedditSubscription) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { redditRepository.deleteSubscription(subscription.id) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          reload()
          _state.update { it.copy(message = "${subscription.title}の購読を解除しました") }
        }
        .onFailure(::showError)
    }
  }

  fun subscribeThread(article: Article) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { redditRepository.subscribeThread(article.url) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          reload()
          _state.update {
            it.copy(message = "スレッドを購読しました。今後の新着コメントを未読として表示します")
          }
        }
        .onFailure(::showError)
    }
  }

  fun unsubscribeThread(article: Article) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { redditRepository.unsubscribeThread(article.url) }
        .onSuccess {
          backupChangeScheduler.scheduleAfterChange()
          reload()
          _state.update { it.copy(message = "スレッドの購読を解除しました") }
        }
        .onFailure(::showError)
    }
  }

  fun markRead(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { bookmarkRepository.isBookmarked(article.id) },
  ) {
    articleRepository.markArticleRead(article.id)
  }

  fun markUnread(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { bookmarkRepository.isBookmarked(article.id) },
  ) {
    articleRepository.markArticleUnread(article.id)
  }

  fun saveAndRead(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { true },
  ) {
    bookmarkRepository.saveAndReadArticle(article.id)
  }

  fun readLater(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { true },
  ) {
    bookmarkRepository.markReadLater(article.id)
  }

  fun unsave(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { true },
  ) {
    bookmarkRepository.unsaveArticle(article.id)
  }

  fun removeReadLater(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { true },
  ) {
    bookmarkRepository.removeReadLater(article.id)
  }

  fun markAllUnreadAsRead() {
    val visible = _state.value.unread.filterNot { it.id in _state.value.hiddenArticleIds }
    if (visible.isEmpty()) return
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + visible.map(Article::id)) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        var scheduleBackup = false
        visible.forEach { article ->
          if (!scheduleBackup && bookmarkRepository.isBookmarked(article.id)) {
            scheduleBackup = true
          }
          articleRepository.markArticleRead(article.id)
        }
        scheduleBackup
      }.onSuccess { scheduleBackup ->
        if (scheduleBackup) backupChangeScheduler.scheduleAfterChange()
        reload()
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - visible.map(Article::id).toSet(),
            message = "${visible.size}件を既読にしました",
          )
        }
      }.onFailure { error ->
        reload()
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - visible.map(Article::id).toSet(),
            message = "すべて既読にできませんでした: ${error.userMessage()}",
          )
        }
      }
    }
  }

  private fun performArticleAction(
    article: Article,
    shouldScheduleBackup: suspend () -> Boolean,
    action: suspend () -> Unit,
  ) {
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + article.id) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val scheduleBackup = shouldScheduleBackup()
        action()
        scheduleBackup
      }.onSuccess { scheduleBackup ->
        if (scheduleBackup) backupChangeScheduler.scheduleAfterChange()
        reload()
        _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds - article.id) }
      }.onFailure { error ->
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - article.id,
            message = "操作を反映できませんでした: ${error.userMessage()}",
          )
        }
      }
    }
  }

  private suspend fun reload() {
    reloadMutex.withLock {
      runCatching {
        RedditSnapshot(
          unread = articleRepository.listUnreadArticles().filter(Article::isRedditArticle),
          history = articleRepository.listHistoryArticles().filter(Article::isRedditArticle),
          readLater = bookmarkRepository.listReadLaterArticles().filter { it.article.isRedditArticle() },
          subscriptions = redditRepository.listSubscriptions(),
        )
      }.onSuccess { snapshot ->
        _state.update {
          it.copy(
            initialized = true,
            unread = snapshot.unread,
            history = snapshot.history,
            readLater = snapshot.readLater,
            subscriptions = snapshot.subscriptions,
          )
        }
      }.onFailure { error ->
        _state.update {
          it.copy(initialized = true, message = "Redditを読み込めませんでした: ${error.userMessage()}")
        }
      }
    }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(message = error.userMessage()) }
  }

  class Factory(
    private val redditRepository: RedditRepository,
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val backupChangeScheduler: BackupChangeScheduler,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RedditViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return RedditViewModel(
        redditRepository,
        articleRepository,
        bookmarkRepository,
        backupChangeScheduler,
      ) as T
    }
  }
}

private data class RedditSnapshot(
  val unread: List<Article>,
  val history: List<Article>,
  val readLater: List<BookmarkedArticle>,
  val subscriptions: List<RedditSubscription>,
)

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
