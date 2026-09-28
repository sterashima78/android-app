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
  private val aiTaskController: LibraryOrganizationAiTaskController,
  private val batchScheduler: LibraryOrganizationBatchScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryOrganizationUiState())
  val state: StateFlow<LibraryOrganizationUiState> = _state.asStateFlow()
  private val suggestionRequestIds = mutableMapOf<LibraryBookKey, String>()
  private var seriesRequestId: String? = null

  init {
    viewModelScope.launch {
      resumeRecoverableAiTask()
      refresh()
    }
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
    val consumesAiSuggestion = _state.value.suggestions.containsKey(key)
    viewModelScope.launch {
      _state.update { it.copy(savingBook = key) }
      runCatching { repository.save(book, draft) }
        .onSuccess {
          if (consumesAiSuggestion) {
            suggestionRequestIds.remove(key)?.let { requestId ->
              viewModelScope.launch { runCatching { aiTaskController.dismiss(requestId) } }
            }
          }
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
    if (_state.value.suggestingBook != null) {
      _state.update { it.copy(message = "個別のAI候補生成が完了してから次の候補を生成してください") }
      return
    }
    val key = book.organizationKey()
    viewModelScope.launch {
      suggestionRequestIds.remove(key)?.let { previousRequestId ->
        runCatching { aiTaskController.dismiss(previousRequestId) }
      }
      _state.update {
        it.copy(
          suggestingBook = key,
          suggestions = it.suggestions - key,
        )
      }
      runCatching { aiTaskController.enqueueSuggestion(book) }
        .onSuccess { requestId ->
          suggestionRequestIds[key] = requestId
          observeSuggestionTask(requestId, key)
        }
        .onFailure { error ->
          _state.update {
            it.copy(
              suggestingBook = if (it.suggestingBook == key) null else it.suggestingBook,
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
      runCatching { aiTaskController.enqueueSeriesReorganization(firstBook) }
        .onSuccess { requestId ->
          seriesRequestId = requestId
          observeSeriesTask(requestId, seriesName)
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

  private suspend fun resumeRecoverableAiTask() {
    val references = runCatching { aiTaskController.recoverableTasks() }
      .getOrElse { error ->
        _state.update { it.copy(message = error.message ?: "AIタスクの状態を読み込めませんでした") }
        return
      }

    references.forEach { reference ->
      when (reference.kind) {
        LibraryOrganizationAiTaskKind.SUGGESTION -> {
          suggestionRequestIds[reference.bookKey] = reference.requestId
          _state.update { it.copy(suggestingBook = reference.bookKey) }
          viewModelScope.launch {
            observeSuggestionTask(reference.requestId, reference.bookKey)
          }
        }
        LibraryOrganizationAiTaskKind.SERIES_REORGANIZATION -> {
          seriesRequestId = reference.requestId
          _state.update { it.copy(reorganizingSeriesBook = reference.bookKey) }
          viewModelScope.launch {
            observeSeriesTask(
              requestId = reference.requestId,
              seriesName = reference.seriesName ?: "対象シリーズ",
            )
          }
        }
      }
    }
  }
  private suspend fun observeSuggestionTask(
    requestId: String,
    key: LibraryBookKey,
  ) {
    while (true) {
      val snapshot = runCatching { aiTaskController.snapshot(requestId) }
        .getOrElse { error ->
          _state.update {
            it.copy(
              suggestingBook = null,
              message = error.message ?: "AIの整理候補を生成できませんでした",
            )
          }
          return
        }
      when (snapshot.state) {
        LibraryOrganizationAiTaskState.QUEUED,
        LibraryOrganizationAiTaskState.RUNNING -> {
          _state.update { it.copy(suggestingBook = key) }
          delay(AI_TASK_REFRESH_INTERVAL_MS)
        }
        LibraryOrganizationAiTaskState.SUCCEEDED -> {
          val suggestion = snapshot.suggestion
          if (suggestion == null) {
            aiTaskController.dismiss(requestId)
            if (suggestionRequestIds[key] == requestId) suggestionRequestIds.remove(key)
            _state.update {
              it.copy(
                suggestingBook = if (it.suggestingBook == key) null else it.suggestingBook,
                message = "AIの整理候補を読み込めませんでした",
              )
            }
          } else {
            _state.update {
              it.copy(
                suggestingBook = if (it.suggestingBook == key) null else it.suggestingBook,
                suggestions = it.suggestions + (key to suggestion),
              )
            }
          }
          return
        }
        LibraryOrganizationAiTaskState.FAILED,
        LibraryOrganizationAiTaskState.CANCELLED -> {
          aiTaskController.dismiss(requestId)
          if (suggestionRequestIds[key] == requestId) suggestionRequestIds.remove(key)
          _state.update {
            it.copy(
              suggestingBook = if (it.suggestingBook == key) null else it.suggestingBook,
              message = snapshot.error?.takeIf(String::isNotBlank) ?: "AIの整理候補を生成できませんでした",
            )
          }
          return
        }
      }
    }
  }

  private suspend fun observeSeriesTask(
    requestId: String,
    seriesName: String,
  ) {
    while (true) {
      val snapshot = runCatching { aiTaskController.snapshot(requestId) }
        .getOrElse { error ->
          _state.update {
            it.copy(
              reorganizingSeriesBook = null,
              message = error.message ?: "シリーズを再整理できませんでした",
            )
          }
          return
        }
      when (snapshot.state) {
        LibraryOrganizationAiTaskState.QUEUED,
        LibraryOrganizationAiTaskState.RUNNING -> delay(AI_TASK_REFRESH_INTERVAL_MS)
        LibraryOrganizationAiTaskState.SUCCEEDED -> {
          val result = snapshot.seriesResult
          if (result == null) {
            aiTaskController.dismiss(requestId)
            if (seriesRequestId == requestId) seriesRequestId = null
            _state.update {
              it.copy(
                reorganizingSeriesBook = null,
                message = "シリーズ再整理の結果を読み込めませんでした",
              )
            }
            return
          }
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
          aiTaskController.dismiss(requestId)
          if (seriesRequestId == requestId) seriesRequestId = null
          return
        }
        LibraryOrganizationAiTaskState.FAILED,
        LibraryOrganizationAiTaskState.CANCELLED -> {
          aiTaskController.dismiss(requestId)
          if (seriesRequestId == requestId) seriesRequestId = null
          _state.update {
            it.copy(
              reorganizingSeriesBook = null,
              message = snapshot.error?.takeIf(String::isNotBlank) ?: "シリーズを再整理できませんでした",
            )
          }
          return
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
    private val aiTaskController: LibraryOrganizationAiTaskController,
    private val batchScheduler: LibraryOrganizationBatchScheduler,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
      LibraryOrganizationViewModel(repository, aiTaskController, batchScheduler) as T
  }
}

private const val AI_TASK_REFRESH_INTERVAL_MS = 500L


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
  LibraryOrganizationCandidateStatus.FAILED,
  LibraryOrganizationCandidateStatus.SKIPPED,
)

private const val BATCH_REFRESH_INTERVAL_MS = 1_000L
