package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LibraryOrganizationUiState(
  val initialized: Boolean = false,
  val loading: Boolean = false,
  val snapshot: LibraryOrganizationSnapshot = LibraryOrganizationSnapshot(),
  val batch: LibraryOrganizationBatchSnapshot? = null,
  val savingBook: LibraryBookKey? = null,
  val suggestingBook: LibraryBookKey? = null,
  val suggestions: Map<LibraryBookKey, LibraryOrganizationSuggestion> = emptyMap(),
  val candidateActionBook: LibraryBookKey? = null,
  val message: String? = null,
)

class LibraryOrganizationViewModel(
  private val repository: LibraryOrganizationRepository,
  private val suggester: LibraryOrganizationSuggester,
  private val batchScheduler: LibraryOrganizationBatchScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryOrganizationUiState())
  val state: StateFlow<LibraryOrganizationUiState> = _state.asStateFlow()

  init {
    refresh()
    viewModelScope.launch {
      while (isActive) {
        delay(BATCH_REFRESH_INTERVAL_MS)
        refreshBatchSilently()
      }
    }
  }

  fun refresh() {
    viewModelScope.launch {
      _state.update { it.copy(loading = true) }
      runCatching { repository.snapshot() to repository.batchSnapshot() }
        .onSuccess { (snapshot, batch) ->
          _state.update {
            it.copy(
              initialized = true,
              loading = false,
              snapshot = snapshot,
              batch = batch,
            )
          }
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
        repository.snapshot() to repository.batchSnapshot()
      }.onSuccess { (snapshot, batch) ->
        _state.update {
          it.copy(
            snapshot = snapshot,
            batch = batch,
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
    if (_state.value.batch?.status == LibraryOrganizationBatchStatus.RUNNING) {
      _state.update { it.copy(message = "一括AI解析中は個別のAI候補を生成できません") }
      return
    }
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

  fun startBatch(books: List<LibraryBook>) {
    val current = _state.value
    if (!current.initialized || current.loading) {
      _state.update { it.copy(message = "整理情報の読み込み完了後に一括AI解析を開始してください") }
      return
    }
    if (current.suggestingBook != null) {
      _state.update { it.copy(message = "個別のAI候補生成が完了してから一括AI解析を開始してください") }
      return
    }
    viewModelScope.launch {
      runCatching {
        repository.startBatch(books)
        batchScheduler.kick()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update {
          it.copy(
            batch = batch,
            message = "一括AI解析をバックグラウンドで開始しました",
          )
        }
      }.onFailure(::reportCandidateError)
    }
  }

  fun pauseBatch() {
    viewModelScope.launch {
      runCatching {
        repository.pauseBatch()
        batchScheduler.cancel()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update { it.copy(batch = batch, message = "一括AI解析を一時停止しました") }
      }.onFailure(::reportCandidateError)
    }
  }

  fun resumeBatch() {
    viewModelScope.launch {
      runCatching {
        repository.resumeBatch()
        batchScheduler.kick()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update { it.copy(batch = batch, message = "一括AI解析を再開しました") }
      }.onFailure(::reportCandidateError)
    }
  }

  fun updateCandidate(
    candidate: LibraryOrganizationCandidate,
    tagNames: List<String>,
    collectionNames: List<String>,
  ) {
    runCandidateAction(candidate.key) {
      repository.updateCandidate(
        candidate.key,
        LibraryOrganizationDraft(
          tagNames = tagNames,
          collectionNames = collectionNames,
          readingStatus = null,
        ),
      )
      "AI整理候補を更新しました"
    }
  }

  fun acceptCandidate(
    book: LibraryBook,
    candidate: LibraryOrganizationCandidate,
    tagNames: List<String> = candidate.tagNames,
    collectionNames: List<String> = candidate.collectionNames,
  ) {
    runCandidateAction(candidate.key, refreshOrganization = true) {
      repository.acceptCandidate(
        book,
        LibraryOrganizationDraft(
          tagNames = tagNames,
          collectionNames = collectionNames,
          readingStatus = null,
        ),
      )
      "整理候補を採用しました"
    }
  }

  fun deferCandidate(candidate: LibraryOrganizationCandidate) {
    runCandidateAction(candidate.key) {
      repository.deferCandidate(candidate.key)
      "整理候補を保留しました"
    }
  }

  fun rejectCandidate(candidate: LibraryOrganizationCandidate) {
    runCandidateAction(candidate.key) {
      repository.rejectCandidate(candidate.key)
      "整理候補を却下しました"
    }
  }

  fun reopenCandidate(candidate: LibraryOrganizationCandidate) {
    runCandidateAction(candidate.key) {
      repository.reopenCandidate(candidate.key)
      "整理候補を未確認へ戻しました"
    }
  }

  fun retryCandidate(candidate: LibraryOrganizationCandidate) {
    runCandidateAction(candidate.key) {
      repository.retryCandidate(candidate.key)
      batchScheduler.kick()
      "AI解析を再実行します"
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private fun runCandidateAction(
    key: LibraryBookKey,
    refreshOrganization: Boolean = false,
    block: suspend () -> String,
  ) {
    viewModelScope.launch {
      _state.update { it.copy(candidateActionBook = key) }
      runCatching {
        val message = block()
        val batch = repository.batchSnapshot()
        val snapshot = if (refreshOrganization) repository.snapshot() else null
        Triple(message, batch, snapshot)
      }.onSuccess { (message, batch, snapshot) ->
        _state.update {
          it.copy(
            batch = batch,
            snapshot = snapshot ?: it.snapshot,
            candidateActionBook = null,
            message = message,
          )
        }
      }.onFailure { error ->
        _state.update {
          it.copy(
            candidateActionBook = null,
            message = error.message ?: "AI整理候補を更新できませんでした",
          )
        }
      }
    }
  }

  private suspend fun refreshBatchSilently() {
    runCatching { repository.batchSnapshot() }
      .onSuccess { batch ->
        _state.update { state ->
          if (state.batch == batch) state else state.copy(batch = batch)
        }
      }
  }

  private fun reportCandidateError(error: Throwable) {
    _state.update {
      it.copy(message = error.message ?: "一括AI整理を操作できませんでした")
    }
  }

  class Factory(
    private val repository: LibraryOrganizationRepository,
    private val suggester: LibraryOrganizationSuggester,
    private val batchScheduler: LibraryOrganizationBatchScheduler,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      LibraryOrganizationViewModel(repository, suggester, batchScheduler) as T
  }
}

private const val BATCH_REFRESH_INTERVAL_MS = 1_000L
