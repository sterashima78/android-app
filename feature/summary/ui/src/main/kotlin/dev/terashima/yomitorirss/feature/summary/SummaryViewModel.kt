package dev.terashima.yomitorirss.feature.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SummaryUiState(
  val article: Article? = null,
  val text: String? = null,
  val loading: Boolean = false,
  val message: String? = null,
)

class SummaryViewModel(
  private val repository: SummaryRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(SummaryUiState())
  val state: StateFlow<SummaryUiState> = _state.asStateFlow()

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

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
