package dev.terashima.yomitorirss.feature.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SummaryReviewUiState(
  val articleId: String? = null,
  val text: String? = null,
  val loading: Boolean = false,
  val error: String? = null,
)

data class SummaryUiState(
  val article: Article? = null,
  val text: String? = null,
  val loading: Boolean = false,
  val message: String? = null,
  val review: SummaryReviewUiState = SummaryReviewUiState(),
)

class SummaryViewModel(
  private val repository: SummaryRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(SummaryUiState())
  val state: StateFlow<SummaryUiState> = _state.asStateFlow()
  private var reviewJob: Job? = null

  fun summarize(
    article: Article,
    forceRefresh: Boolean = false,
    replaceBookmarkTags: Boolean = false,
  ) {
    require(!replaceBookmarkTags || forceRefresh) {
      "Bookmark tag replacement requires a force-refresh summary request"
    }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        if (replaceBookmarkTags) {
          repository.requestBookmarkEnrichmentRefresh(article.id)
        } else {
          repository.request(article.id, forceRefresh)
        }
      }
        .onSuccess { result ->
          when (result) {
            is SummaryRequestResult.Cached -> {
              _state.update {
                it.copy(
                  article = article,
                  text = result.summary,
                  loading = false,
                )
              }
            }

            SummaryRequestResult.Processing -> {
              _state.update { it.copy(loading = false, message = "要約はバックグラウンドで処理中です") }
            }

            is SummaryRequestResult.PreviousFailure -> {
              _state.update {
                it.copy(
                  article = article,
                  text = "前回の要約に失敗しました: ${result.error}",
                  loading = false,
                )
              }
            }

            is SummaryRequestResult.Enqueued -> {
              _state.update {
                it.copy(
                  loading = false,
                  message = if (result.accepted) {
                    when {
                      replaceBookmarkTags -> "要約とタグの再生成をキューに追加しました"
                      result.forceRefresh -> "要約の再生成をキューに追加しました"
                      else -> "要約をキューに追加しました"
                    }
                  } else {
                    "要約はすでにキューに入っているか処理中です"
                  },
                )
              }
            }
          }
        }
        .onFailure { error ->
          _state.update { it.copy(loading = false, message = error.userMessage()) }
        }
    }
  }

  fun prepareReview(article: Article) {
    val review = _state.value.review
    if (
      review.articleId == article.id &&
      (review.text != null || review.loading || review.error != null)
    ) {
      return
    }
    loadReview(article = article, forceRefresh = false)
  }

  fun retryReview(article: Article) {
    loadReview(article = article, forceRefresh = true)
  }

  private fun loadReview(
    article: Article,
    forceRefresh: Boolean,
  ) {
    reviewJob?.cancel()
    reviewJob = viewModelScope.launch(Dispatchers.IO) {
      _state.update {
        it.copy(
          review = SummaryReviewUiState(
            articleId = article.id,
            loading = true,
          ),
        )
      }

      try {
        if (!forceRefresh) {
          repository.findSummary(article.id)?.let { summary ->
            setReviewSummary(article.id, summary)
            return@launch
          }
        }

        when (val result = repository.request(article.id, forceRefresh)) {
          is SummaryRequestResult.Cached -> setReviewSummary(article.id, result.summary)
          is SummaryRequestResult.PreviousFailure -> setReviewError(article.id, "前回の要約に失敗しました: ${result.error}")
          SummaryRequestResult.Processing,
          is SummaryRequestResult.Enqueued -> waitForReviewSummary(article.id)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Throwable) {
        setReviewError(article.id, "要約を準備できませんでした: ${error.userMessage()}")
      }
    }
  }

  private suspend fun waitForReviewSummary(articleId: String) {
    while (currentCoroutineContext().isActive && _state.value.review.articleId == articleId) {
      delay(REVIEW_SUMMARY_POLL_INTERVAL_MS)
      when (val result = repository.request(articleId, forceRefresh = false)) {
        is SummaryRequestResult.Cached -> {
          setReviewSummary(articleId, result.summary)
          return
        }

        is SummaryRequestResult.PreviousFailure -> {
          setReviewError(articleId, "要約に失敗しました: ${result.error}")
          return
        }

        SummaryRequestResult.Processing,
        is SummaryRequestResult.Enqueued -> Unit
      }
    }
  }

  private fun setReviewSummary(articleId: String, summary: String) {
    _state.update { state ->
      if (state.review.articleId != articleId) {
        state
      } else {
        state.copy(
          review = state.review.copy(
            text = summary,
            loading = false,
            error = null,
          ),
        )
      }
    }
  }

  private fun setReviewError(articleId: String, message: String) {
    _state.update { state ->
      if (state.review.articleId != articleId) {
        state
      } else {
        state.copy(
          review = state.review.copy(
            text = null,
            loading = false,
            error = message,
          ),
        )
      }
    }
  }

  fun dismissSummary() {
    _state.update { it.copy(article = null, text = null, loading = false) }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  class Factory(
    private val repository: SummaryRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(SummaryViewModel::class.java)) {
        "Unknown ViewModel class: ${modelClass.name}"
      }
      @Suppress("UNCHECKED_CAST")
      return SummaryViewModel(repository) as T
    }
  }
}

private const val REVIEW_SUMMARY_POLL_INTERVAL_MS = 1_500L

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
