package dev.terashima.yomitorirss.feature.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnowledgeUiState(
  val initialized: Boolean = false,
  val pages: List<KnowledgePageSummary> = emptyList(),
  val query: String = "",
  val selectedPage: KnowledgePage? = null,
  val building: Boolean = false,
  val lastBuild: KnowledgeBuildResult? = null,
  val message: String? = null,
)

class KnowledgeViewModel(
  private val repository: KnowledgeRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(KnowledgeUiState())
  val state: StateFlow<KnowledgeUiState> = _state.asStateFlow()

  init {
    refresh()
  }

  fun updateQuery(query: String) {
    _state.update { it.copy(query = query) }
    refresh()
  }

  fun openPage(id: String) {
    viewModelScope.launch {
      runCatching { repository.findPage(id) }
        .onSuccess { page -> _state.update { it.copy(selectedPage = page, message = null) } }
        .onFailure(::reportError)
    }
  }

  fun closePage() {
    _state.update { it.copy(selectedPage = null) }
  }

  fun rebuild() {
    if (_state.value.building) return
    _state.update { it.copy(building = true, message = null) }
    viewModelScope.launch {
      runCatching { repository.rebuild() }
        .onSuccess { result ->
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = null,
              building = false,
              lastBuild = result,
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(building = false) }
          reportError(error)
        }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun refresh() {
    val query = _state.value.query
    viewModelScope.launch {
      runCatching { repository.listPages(query) }
        .onSuccess { pages ->
          if (_state.value.query == query) {
            _state.update { it.copy(initialized = true, pages = pages) }
          }
        }
        .onFailure(::reportError)
    }
  }

  private fun reportError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        message = error.message?.takeIf(String::isNotBlank) ?: "ナレッジの処理に失敗しました",
      )
    }
  }

  class Factory(
    private val repository: KnowledgeRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(KnowledgeViewModel::class.java))
      return KnowledgeViewModel(repository) as T
    }
  }
}
