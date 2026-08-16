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
  val reorganizingSeriesBook: LibraryBookKey? = null,
  val suggestions: Map<LibraryBookKey, LibraryOrganizationSuggestion> = emptyMap(),
  val message: String? = null,
)

class LibraryOrganizationViewModel(
  private val repository: LibraryOrganizationRepository,
  private val suggester: LibraryOrganizationSuggester,
  private val batchScheduler: LibraryOrganizationBatchScheduler,
) : ViewModel() {
  private val metadataOrganizer = LibraryMetadataOrganizer(repository, suggester)
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
    if (_state.value.reorganizingSeriesBook != null) {
      _state.update { it.copy(message = "シリーズの再整理が完了してから編集してください") }
      return
    }
    val key = book.organizationKey()
    viewModelScope.launch {
      _state.update { it.copy(savingBook = key) }
      runCatching { repository.save(book, draft) }
        .onSuccess {
          val refreshed = runCatching { repository.snapshot() to repository.batchSnapshot() }.getOrNull()
          _state.update {
            it.copy(
              snapshot = refreshed?.first ?: it.snapshot,
              batch = refreshed?.second ?: it.batch,
              savingBook = null,
              suggestions = it.suggestions - key,
              message = if (refreshed != null) {
                "整理情報を保存しました"
              } else {
                "整理情報を保存しました。表示は次回の再読込で更新されます"
              },
            )
          }
        }
        .onFailure { error ->
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
    if (_state.value.reorganizingSeriesBook != null) {
      _state.update { it.copy(message = "シリーズの再整理中は個別のAI候補を生成できません") }
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
    if (current.reorganizingSeriesBook != null) {
      _state.update { it.copy(message = "シリーズの再整理が完了してから一括AI解析を開始してください") }
      return
    }
    viewModelScope.launch {
      runCatching {
        discardPreviousNonActiveResults()
        repository.startBatch(books)
        batchScheduler.kick()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update {
          it.copy(
            batch = batch,
            message = "一括AI整理をバックグラウンドで開始しました",
          )
        }
      }.onFailure(::reportBatchError)
    }
  }

  fun reorganizeSeries(books: List<LibraryBook>) {
    val current = _state.value
    val firstBook = books.firstOrNull()
    val seriesName = firstBook?.series?.name?.trim().orEmpty()
    if (firstBook == null || seriesName.isEmpty()) {
      _state.update { it.copy(message = "シリーズ情報が設定された蔵書だけ再整理できます") }
      return
    }
    if (current.batch?.status == LibraryOrganizationBatchStatus.RUNNING) {
      _state.update { it.copy(message = "一括AI解析を一時停止してからシリーズを再整理してください") }
      return
    }
    if (current.suggestingBook != null || current.savingBook != null || current.reorganizingSeriesBook != null) {
      _state.update { it.copy(message = "実行中の整理操作が完了してからシリーズを再整理してください") }
      return
    }

    viewModelScope.launch {
      _state.update { it.copy(reorganizingSeriesBook = firstBook.organizationKey()) }
      runCatching { metadataOrganizer.reorganizeSeries(books) }
        .onSuccess { result ->
          val refreshedSnapshot = runCatching { repository.snapshot() }.getOrNull()
          val message = buildSeriesReorganizationMessage(
            seriesName = seriesName,
            result = result,
            refreshSucceeded = refreshedSnapshot != null,
          )
          _state.update {
            it.copy(
              snapshot = refreshedSnapshot ?: it.snapshot,
              reorganizingSeriesBook = null,
              message = message,
            )
          }
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              reorganizingSeriesBook = null,
              message = error.message ?: "シリーズを再整理できませんでした",
            )
          }
        }
    }
  }

  fun pauseBatch() {
    viewModelScope.launch {
      runCatching {
        repository.pauseBatch()
        batchScheduler.cancel()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update { it.copy(batch = batch, message = "一括AI整理を一時停止しました") }
      }.onFailure(::reportBatchError)
    }
  }

  fun resumeBatch() {
    if (_state.value.reorganizingSeriesBook != null) {
      _state.update { it.copy(message = "シリーズの再整理が完了してから一括AI整理を再開してください") }
      return
    }
    viewModelScope.launch {
      runCatching {
        repository.resumeBatch()
        batchScheduler.kick()
        repository.batchSnapshot()
      }.onSuccess { batch ->
        _state.update { it.copy(batch = batch, message = "一括AI整理を再開しました") }
      }.onFailure(::reportBatchError)
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  private suspend fun discardPreviousNonActiveResults() {
    val batch = repository.batchSnapshot() ?: return
    batch.candidates
      .filter { candidate -> candidate.status in DISCARDABLE_PREVIOUS_RESULTS }
      .forEach { candidate -> repository.rejectCandidate(candidate.key) }
  }

  private suspend fun refreshBatchSilently() {
    runCatching { repository.batchSnapshot() }
      .onSuccess { batch ->
        _state.update { state ->
          if (state.batch == batch) state else state.copy(batch = batch)
        }
      }
  }

  private fun reportBatchError(error: Throwable) {
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

private fun buildSeriesReorganizationMessage(
  seriesName: String,
  result: LibrarySeriesReorganizationResult,
  refreshSucceeded: Boolean,
): String {
  val base = if (result.failed == 0) {
    "シリーズ「$seriesName」を ${result.updated} 冊再整理しました"
  } else {
    "シリーズ「$seriesName」を ${result.updated} / ${result.total} 冊再整理しました。${result.failed} 冊は既存情報を保持しました"
  }
  return if (refreshSucceeded) base else "$base。表示は次回の再読込で更新されます"
}

private val DISCARDABLE_PREVIOUS_RESULTS = setOf(
  LibraryOrganizationCandidateStatus.PENDING_REVIEW,
  LibraryOrganizationCandidateStatus.DEFERRED,
  LibraryOrganizationCandidateStatus.FAILED,
  LibraryOrganizationCandidateStatus.SKIPPED,
)

private const val BATCH_REFRESH_INTERVAL_MS = 1_000L
