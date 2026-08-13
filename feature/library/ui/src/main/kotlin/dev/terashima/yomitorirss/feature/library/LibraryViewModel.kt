package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
  val initialized: Boolean = false,
  val syncing: Boolean = false,
  val importingSource: LibrarySource? = null,
  val books: List<LibraryBook> = emptyList(),
  val hiddenBooks: List<LibraryBook> = emptyList(),
  val sourceStates: Map<LibrarySource, LibrarySourceState> = emptyMap(),
  val kindleCoverEnrichmentEnabled: Boolean = false,
  val message: String? = null,
)

class LibraryViewModel(
  private val repository: LibraryRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  init {
    reload()
  }

  fun syncGooglePlayBooks(accessToken: String, accountLabel: String?) {
    if (_state.value.syncing || _state.value.importingSource != null) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(syncing = true) }
      runCatching { repository.syncGooglePlayBooks(accessToken, accountLabel) }
        .onSuccess { result ->
          loadSnapshot(
            message = "Google Play Books から ${result.importedCount} 冊を同期しました",
          )
        }
        .onFailure(::showError)
    }
  }

  fun importAmazonLibrary(
    source: LibrarySource,
    fileName: String?,
    openInputStream: () -> InputStream,
  ) {
    require(source == LibrarySource.KINDLE || source == LibrarySource.AUDIBLE)
    if (_state.value.syncing || _state.value.importingSource != null) return
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(importingSource = source) }
      runCatching {
        openInputStream().use { input ->
          repository.importAmazonLibrary(source, fileName, input)
        }
      }
        .onSuccess { result ->
          loadSnapshot(message = "${source.label} から ${result.importedCount} 冊をインポートしました")
        }
        .onFailure(::showError)
    }
  }

  fun hideBook(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.hideBook(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」を非表示にしました") }
        .onFailure(::showError)
    }
  }

  fun restoreBook(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.restoreBook(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」を蔵書に戻しました") }
        .onFailure(::showError)
    }
  }

  fun setBookSeries(
    book: LibraryBook,
    seriesName: String,
    position: Int?,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        repository.setBookSeries(
          book = book,
          series = LibrarySeries(name = seriesName, position = position),
        )
      }
        .onSuccess { loadSnapshot(message = "「${book.title}」のシリーズを更新しました") }
        .onFailure(::showError)
    }
  }

  fun clearBookSeries(book: LibraryBook) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.clearBookSeries(book) }
        .onSuccess { loadSnapshot(message = "「${book.title}」をシリーズから外しました") }
        .onFailure(::showError)
    }
  }

  fun setKindleCoverEnrichmentEnabled(enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.setKindleCoverEnrichmentEnabled(enabled) }
        .onSuccess {
          loadSnapshot(
            message = if (enabled) {
              "Kindle の表紙補完を有効にしました"
            } else {
              "Kindle の表紙補完を無効にしました"
            },
          )
        }
        .onFailure(::showError)
    }
  }

  fun reportError(error: Throwable) {
    showError(error)
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun reload() {
    viewModelScope.launch(Dispatchers.IO) { loadSnapshot() }
  }

  private suspend fun loadSnapshot(message: String? = null) {
    runCatching { repository.snapshot() }
      .onSuccess { snapshot ->
        _state.update {
          it.copy(
            initialized = true,
            syncing = false,
            importingSource = null,
            books = snapshot.books,
            hiddenBooks = snapshot.hiddenBooks,
            sourceStates = snapshot.sourceStates,
            kindleCoverEnrichmentEnabled = snapshot.kindleCoverEnrichmentEnabled,
            message = message,
          )
        }
      }
      .onFailure(::showError)
  }

  private fun showError(error: Throwable) {
    _state.update {
      it.copy(
        initialized = true,
        syncing = false,
        importingSource = null,
        message = error.message ?: "蔵書の操作に失敗しました",
      )
    }
  }

  class Factory(private val repository: LibraryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return LibraryViewModel(repository) as T
    }
  }
}
