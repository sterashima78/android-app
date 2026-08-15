package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryOrganizationUiState(
  val initialized: Boolean = false,
  val loading: Boolean = false,
  val snapshot: LibraryOrganizationSnapshot = LibraryOrganizationSnapshot(),
  val savingBook: LibraryBookKey? = null,
  val suggestingBook: LibraryBookKey? = null,
  val suggestions: Map<LibraryBookKey, LibraryOrganizationSuggestion> = emptyMap(),
  val message: String? = null,
)

class LibraryOrganizationViewModel(
  private val repository: LibraryOrganizationRepository,
  private val suggester: LibraryOrganizationSuggester,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryOrganizationUiState())
  val state: StateFlow<LibraryOrganizationUiState> = _state.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true) }
      runCatching { repository.snapshot() }
        .onSuccess { snapshot ->
          _state.update { it.copy(initialized = true, loading = false, snapshot = snapshot) }
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              initialized = true,
              loading = false,
              message = error.message ?: "蔵書の整理情報を読み込めませんでした",
            )
          }
        }
    }
  }

  fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  ) {
    val key = book.organizationKey()
    viewModelScope.launch {
      _state.update { it.copy(savingBook = key) }
      runCatching {
        repository.save(book, draft)
        repository.snapshot()
      }.onSuccess { snapshot ->
        _state.update {
          it.copy(
            snapshot = snapshot,
            savingBook = null,
            suggestions = it.suggestions - key,
            message = "整理情報を保存しました",
          )
        }
      }.onFailure { error ->
        _state.update {
          it.copy(
            savingBook = null,
            message = error.message ?: "整理情報を保存できませんでした",
          )
        }
      }
    }
  }

  fun suggest(book: LibraryBook) {
    val key = book.organizationKey()
    viewModelScope.launch {
      _state.update { it.copy(suggestingBook = key) }
      runCatching {
        suggester.suggest(
          book = book,
          existingTags = _state.value.snapshot.tags.map(LibraryOrganizationTag::name),
          existingCollections = _state.value.snapshot.collections.map(LibraryCollection::name),
        )
      }.onSuccess { suggestion ->
        _state.update {
          it.copy(
            suggestingBook = null,
            suggestions = it.suggestions + (key to suggestion),
          )
        }
      }.onFailure { error ->
        _state.update {
          it.copy(
            suggestingBook = null,
            message = error.message ?: "AIの整理候補を生成できませんでした",
          )
        }
      }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  class Factory(
    private val repository: LibraryOrganizationRepository,
    private val suggester: LibraryOrganizationSuggester,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      LibraryOrganizationViewModel(repository, suggester) as T
  }
}
