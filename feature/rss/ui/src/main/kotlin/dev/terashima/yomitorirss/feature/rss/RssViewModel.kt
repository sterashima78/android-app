package dev.terashima.yomitorirss.feature.rss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
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

data class RssUiState(
  val initialized: Boolean = false,
  val unread: List<Article> = emptyList(),
  val history: List<Article> = emptyList(),
  val readLater: List<BookmarkedArticle> = emptyList(),
  val hiddenArticleIds: Set<String> = emptySet(),
  val message: String? = null,
)

class RssViewModel(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val articleSelector: (Article) -> Boolean = { true },
) : ViewModel() {
  private val _state = MutableStateFlow(RssUiState())
  val state: StateFlow<RssUiState> = _state.asStateFlow()
  private val reloadMutex = Mutex()
  private val reviewMutationMutex = Mutex()

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

  fun markRead(article: Article) = performArticleAction(article) {
    articleRepository.markArticleRead(article.id)
  }

  fun markUnread(article: Article) = performArticleAction(article) {
    articleRepository.markArticleUnread(article.id)
  }

  fun saveAndRead(article: Article) = performArticleAction(article) {
    bookmarkRepository.saveAndReadArticle(article.id)
  }

  fun readLater(article: Article) = performArticleAction(article) {
    bookmarkRepository.markReadLater(article.id)
  }

  fun unsave(article: Article) = performArticleAction(article) {
    bookmarkRepository.unsaveArticle(article.id)
  }

  fun removeReadLater(article: Article) = performArticleAction(article) {
    bookmarkRepository.removeReadLater(article.id)
  }

  fun reviewUnsave(article: Article) = performReviewBookmarkAction(article) {
    bookmarkRepository.unsaveArticle(article.id)
  }

  fun reviewRemoveReadLater(article: Article) = performReviewBookmarkAction(article) {
    bookmarkRepository.removeReadLater(article.id)
  }

  fun restoreReadLater(bookmarkedArticle: BookmarkedArticle) {
    viewModelScope.launch(Dispatchers.IO) {
      reviewMutationMutex.withLock {
        runCatching {
          bookmarkRepository.markReadLater(bookmarkedArticle.article.id)
          bookmarkRepository.replaceArticleTags(
            bookmarkedArticle.article.id,
            bookmarkedArticle.tags.mapTo(mutableSetOf()) { it.id },
          )
        }.onSuccess {
          reload()
        }.onFailure { error ->
          reload()
          _state.update {
            it.copy(message = "元に戻せませんでした: ${error.userMessage()}")
          }
        }
      }
    }
  }

  fun setArticleContentType(article: Article, contentType: ContentType?) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { articleRepository.setArticleContentType(article.id, contentType) }
        .onSuccess {
          reload()
          _state.update { it.copy(message = "コンテンツ種別を変更しました") }
        }
        .onFailure { error ->
          _state.update { it.copy(message = "コンテンツ種別を変更できませんでした: ${error.userMessage()}") }
        }
    }
  }

  fun markAllUnreadAsRead() {
    val visible = _state.value.unread.filterNot { it.id in _state.value.hiddenArticleIds }
    if (visible.isEmpty()) return
    _state.update { it.copy(unread = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        visible.forEach { article -> articleRepository.markArticleRead(article.id) }
      }.onSuccess {
        reload()
        _state.update { it.copy(message = "${visible.size}件を既読にしました") }
      }.onFailure { error ->
        reload()
        _state.update { it.copy(message = "すべて既読にできませんでした: ${error.userMessage()}") }
      }
    }
  }

  private fun performArticleAction(
    article: Article,
    action: suspend () -> Unit,
  ) {
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + article.id) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }
        .onSuccess {
          reload()
          _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds - article.id) }
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              hiddenArticleIds = it.hiddenArticleIds - article.id,
              message = "操作を反映できなかったため元に戻しました: ${error.userMessage()}",
            )
          }
        }
    }
  }

  private fun performReviewBookmarkAction(
    article: Article,
    action: suspend () -> Unit,
  ) {
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + article.id) }
    viewModelScope.launch(Dispatchers.IO) {
      reviewMutationMutex.withLock {
        runCatching { action() }
          .onSuccess {
            reload()
            _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds - article.id) }
          }
          .onFailure { error ->
            _state.update {
              it.copy(
                hiddenArticleIds = it.hiddenArticleIds - article.id,
                message = "操作を反映できなかったため元に戻しました: ${error.userMessage()}",
              )
            }
          }
      }
    }
  }

  private suspend fun reload() {
    reloadMutex.withLock {
      runCatching {
        Triple(
          articleRepository.listUnreadArticles().filter(articleSelector),
          articleRepository.listHistoryArticles().filter(articleSelector),
          bookmarkRepository.listReadLaterArticles().filter { articleSelector(it.article) },
        )
      }.onSuccess { (unread, history, readLater) ->
        _state.update {
          it.copy(
            initialized = true,
            unread = unread,
            history = history,
            readLater = readLater,
          )
        }
      }.onFailure { error ->
        _state.update { it.copy(initialized = true, message = "記事を読み込めませんでした: ${error.userMessage()}") }
      }
    }
  }

  class Factory(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val articleSelector: (Article) -> Boolean = { true },
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RssViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RssViewModel(articleRepository, bookmarkRepository, articleSelector) as T
    }
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
