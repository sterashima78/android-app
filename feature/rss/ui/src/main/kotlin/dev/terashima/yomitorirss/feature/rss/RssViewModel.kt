package dev.terashima.yomitorirss.feature.rss

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
  val recommendationPolicy: RssRecommendationPolicy = RssRecommendationPolicy(),
  val recommendationAssessments: Map<String, RssRecommendationAssessment> = emptyMap(),
  val recommendationPendingFeedbackCount: Int = 0,
  val recommendationLearning: Boolean = false,
  val message: String? = null,
)

class RssViewModel(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val recommendationService: RssRecommendationService? = null,
  private val articleSelector: (Article) -> Boolean = { true },
) : ViewModel() {
  private val _state = MutableStateFlow(RssUiState())
  val state: StateFlow<RssUiState> = _state.asStateFlow()
  private val reloadMutex = Mutex()
  private val recommendationMutex = Mutex()
  private var recommendationRefreshJob: Job? = null
  private var recommendationLearningJob: Job? = null

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

  fun markAsExclusionReference(article: Article) {
    val service = recommendationService
    if (service == null) {
      markRead(article)
      return
    }
    _state.update { it.copy(hiddenArticleIds = it.hiddenArticleIds + article.id) }
    viewModelScope.launch(Dispatchers.IO) {
      try {
        service.recordExclusionFeedback(article)
        articleRepository.markArticleRead(article.id)
        reload()
        val snapshot = service.snapshot(_state.value.unread.map(Article::id))
        applyRecommendationSnapshot(snapshot)
        scheduleFeedbackLearning(snapshot.latestPendingFeedbackAt)
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - article.id,
            message = "除外参考に追加しました",
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        reload()
        _state.update {
          it.copy(
            hiddenArticleIds = it.hiddenArticleIds - article.id,
            message = "除外参考を登録できませんでした: ${error.userMessage()}",
          )
        }
      }
    }
  }

  fun saveRecommendationCondition(condition: String) {
    val service = recommendationService ?: return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        service.saveManualCondition(condition)
        val snapshot = service.snapshot(_state.value.unread.map(Article::id))
        applyRecommendationSnapshot(snapshot)
        scheduleRecommendationRefresh(_state.value.unread)
        _state.update { it.copy(message = "推薦の除外条件を保存しました") }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update { it.copy(message = "除外条件を保存できませんでした: ${error.userMessage()}") }
      }
    }
  }

  fun resetRecommendationLearning() {
    val service = recommendationService ?: return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        service.resetLearnedCondition()
        val snapshot = service.snapshot(_state.value.unread.map(Article::id))
        applyRecommendationSnapshot(snapshot)
        scheduleRecommendationRefresh(_state.value.unread)
        _state.update { it.copy(message = "学習した除外条件をリセットしました") }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        _state.update { it.copy(message = "学習条件をリセットできませんでした: ${error.userMessage()}") }
      }
    }
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
      runCatching {
        bookmarkRepository.restoreReadLater(
          bookmarkedArticle.article.id,
          bookmarkedArticle.tags.toSet(),
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
        recommendationService?.let { service ->
          val snapshot = service.snapshot(unread.map(Article::id))
          applyRecommendationSnapshot(snapshot)
          scheduleFeedbackLearning(snapshot.latestPendingFeedbackAt)
          scheduleRecommendationRefresh(unread)
        }
      }.onFailure { error ->
        _state.update { it.copy(initialized = true, message = "記事を読み込めませんでした: ${error.userMessage()}") }
      }
    }
  }

  private fun scheduleRecommendationRefresh(unread: List<Article>) {
    val service = recommendationService ?: return
    recommendationRefreshJob?.cancel()
    recommendationRefreshJob = viewModelScope.launch(Dispatchers.IO) {
      recommendationMutex.withLock {
        try {
          applyRecommendationSnapshot(service.refresh(unread))
        } catch (error: CancellationException) {
          throw error
        } catch (_: Throwable) {
          // Individual inference failures are persisted as Unscored by the service.
        }
      }
    }
  }

  private fun scheduleFeedbackLearning(latestPendingAt: Long?) {
    val service = recommendationService ?: return
    if (latestPendingAt == null) {
      recommendationLearningJob?.cancel()
      recommendationLearningJob = null
      return
    }
    recommendationLearningJob?.cancel()
    val waitMillis = (latestPendingAt + RECOMMENDATION_FEEDBACK_DEBOUNCE_MILLIS - System.currentTimeMillis())
      .coerceAtLeast(0L)
    recommendationLearningJob = viewModelScope.launch(Dispatchers.IO) {
      delay(waitMillis)
      _state.update { it.copy(recommendationLearning = true) }
      try {
        val updated = service.improvePendingFeedback()
        val snapshot = service.snapshot(_state.value.unread.map(Article::id))
        applyRecommendationSnapshot(snapshot)
        if (updated != null) scheduleRecommendationRefresh(_state.value.unread)
        scheduleFeedbackLearning(snapshot.latestPendingFeedbackAt)
      } catch (error: CancellationException) {
        throw error
      } finally {
        _state.update { it.copy(recommendationLearning = false) }
      }
    }
  }

  private fun applyRecommendationSnapshot(snapshot: RssRecommendationSnapshot) {
    _state.update {
      it.copy(
        recommendationPolicy = snapshot.policy,
        recommendationAssessments = snapshot.assessments,
        recommendationPendingFeedbackCount = snapshot.pendingFeedbackCount,
      )
    }
  }

  class Factory(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val recommendationService: RssRecommendationService? = null,
    private val articleSelector: (Article) -> Boolean = { true },
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(RssViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return RssViewModel(
        articleRepository = articleRepository,
        bookmarkRepository = bookmarkRepository,
        recommendationService = recommendationService,
        articleSelector = articleSelector,
      ) as T
    }
  }
}

private const val RECOMMENDATION_FEEDBACK_DEBOUNCE_MILLIS = 30_000L

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
