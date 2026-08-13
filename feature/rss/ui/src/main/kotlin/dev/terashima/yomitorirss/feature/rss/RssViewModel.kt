package dev.terashima.yomitorirss.feature.rss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RssUiState(
  val initialized: Boolean = false,
  val unread: List<Article> = emptyList(),
  val readLater: List<BookmarkedArticle> = emptyList(),
  val hiddenArticleIds: Set<String> = emptySet(),
  val message: String? = null,
)

class RssViewModel(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
  private val articleSelector: (Article) -> Boolean = { true },
) : ViewModel() {
  private val _state = MutableStateFlow(RssUiState())
  val state: StateFlow<RssUiState> = _state.asStateFlow()
  private val reloadMutex = Mutex()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { articleRepository.cleanupExpiredArticles() }
      reload()
    }
    viewModelScope.launch(Dispatchers.IO) {
      articleRepository.changes.collect { reload() }
    }
    viewModelScope.launch(Dispatchers.IO) {
      bookmarkRepository.changes.collect { reload() }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) { reload() }
  }

  fun markRead(article: Article) = performArticleAction(
    article = article,
    shouldScheduleBackup = { bookmarkRepository.isBookmarked(article.id) },
  ) {
    articleRepository.markArticleRead(article.id)
  }

  fun saveAndRead(article: Article) = performArticleAction(article, shouldScheduleBackup = { true }) {
    bookmarkRepository.saveAndReadArticle(article.id)
  }

  fun readLater(article: Article) = performArticleAction(article, shouldScheduleBackup = { true }) {
    bookmarkRepository.markReadLater(article.id)
  }

  fun unsave(article: Article) = performArticleAction(article, shouldScheduleBackup = { true }) {
    bookmarkRepository.unsaveArticle(article.id)
  }

  fun removeReadLater(article: Article) = performArticleAction(article, shouldScheduleBackup = { true }) {
    bookmarkRepository.removeReadLater(article.id)
  }

  fun markAllUnreadAsRead() {
    val visible = _state.value.unread.filterNot { it.id in _state.value.hiddenArticleIds }
    if (visible.isEmpty()) return
    _state.update { it.copy(unread = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        var scheduleBackup = false
        visible.forEach { article ->
          if (!scheduleBackup && bookmarkRepository.isBookmarked(article.id)) scheduleBackup = true
          articleRepository.markArticleRead(article.id)
        }
        scheduleBackup
      }.onSuccess { scheduleBackup ->
        reload()
        if (scheduleBackup) backupChangeScheduler.scheduleAfterChange()
        _state.update { it.copy(message = "${visible.size}件を既読にしました") }
      }.onFailure { error ->
        reload()
        _state.update { it.copy(message = "すべて既読にできませんでした: ${error.userMessage()}") }
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
        reload()
        if (scheduleBackup) backupChangeScheduler.scheduleAfterChange()
        _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds - article.id) }
      }.onFailure { error ->
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - article.id,
            message = "操作を反映できなかったため元に戻しました: ${error.userMessage()}",
          )
        }
      }
    }
  }

  private suspend fun reload() {
    reloadMutex.withLock {
      runCatching {
        articleRepository.listUnreadArticles().filter(articleSelector) to
          bookmarkRepository.listReadLaterArticles().filter { articleSelector(it.article) }
      }.onSuccess { (unread, readLater) ->
        _state.update { it.copy(initialized = true, unread = unread, readLater = readLater) }
      }.onFailure { error ->
        _state.update { it.copy(initialized = true, message = "記事を読み込めませんでした: ${error.userMessage()}") }
      }
    }
  }

  class Factory(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val backupChangeScheduler: BackupChangeScheduler,
    @Suppress("UNUSED_PARAMETER") summaryRepository: SummaryRepository,
    private val articleSelector: (Article) -> Boolean = { true },
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RssViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RssViewModel(articleRepository, bookmarkRepository, backupChangeScheduler, articleSelector) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
