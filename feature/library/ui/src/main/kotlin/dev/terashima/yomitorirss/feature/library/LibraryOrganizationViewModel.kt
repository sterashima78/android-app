package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryOrganizationBatchDraft(
  val tagNames: List<String>,
  val collectionNames: List<String>,
)

data class LibraryOrganizationBatchUiState(
  val running: Boolean = false,
  val applying: Boolean = false,
  val total: Int = 0,
  val completed: Int = 0,
  val currentBook: LibraryBookKey? = null,
  val drafts: Map<LibraryBookKey, LibraryOrganizationBatchDraft> = emptyMap(),
  val selectedKeys: Set<LibraryBookKey> = emptySet(),
  val failures: Map<LibraryBookKey, String> = emptyMap(),
)

data class LibraryOrganizationUiState(
  val initialized: Boolean = false,
  val loading: Boolean = false,
  val snapshot: LibraryOrganizationSnapshot = LibraryOrganizationSnapshot(),
  val savingBook: LibraryBookKey? = null,
  val suggestingBook: LibraryBookKey? = null,
  val suggestions: Map<LibraryBookKey, LibraryOrganizationSuggestion> = emptyMap(),
  val batch: LibraryOrganizationBatchUiState = LibraryOrganizationBatchUiState(),
  val message: String? = null,
)

class LibraryOrganizationViewModel(
  private val repository: LibraryOrganizationRepository,
  private val suggester: LibraryOrganizationSuggester,
) : ViewModel() {
  private val _state = MutableStateFlow(LibraryOrganizationUiState())
  val state: StateFlow<LibraryOrganizationUiState> = _state.asStateFlow()
  private var batchJob: Job? = null

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
            batch = it.batch.copy(
              drafts = it.batch.drafts - key,
              selectedKeys = it.batch.selectedKeys - key,
              failures = it.batch.failures - key,
            ),
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
    if (_state.value.batch.running || _state.value.batch.applying) {
      _state.update { it.copy(message = "一括整理の処理中は個別のAI候補を生成できません") }
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

  fun startBatchSuggestion(books: List<LibraryBook>) {
    if (batchJob?.isActive == true || _state.value.batch.applying) return
    if (_state.value.suggestingBook != null) {
      _state.update { it.copy(message = "個別のAI候補生成が完了してから一括整理を開始してください") }
      return
    }

    val snapshot = _state.value.snapshot
    val targets = filterLibraryBooksForOrganization(
      books,
      snapshot,
      LibraryOrganizationFilter.UNORGANIZED,
    )
    if (targets.isEmpty()) {
      _state.update { it.copy(message = "未整理の蔵書はありません") }
      return
    }

    batchJob = viewModelScope.launch {
      val rollingTags = snapshot.tags.map(LibraryOrganizationTag::name).toMutableList()
      val rollingCollections = snapshot.collections.map(LibraryCollection::name).toMutableList()
      _state.update {
        it.copy(
          batch = LibraryOrganizationBatchUiState(
            running = true,
            total = targets.size,
          ),
        )
      }

      try {
        targets.forEachIndexed { index, book ->
          currentCoroutineContext().ensureActive()
          val key = book.organizationKey()
          _state.update { state ->
            state.copy(batch = state.batch.copy(currentBook = key))
          }

          try {
            val suggestion = suggester.suggest(
              book = book,
              existingTags = rollingTags.takeLast(MAX_BATCH_TAXONOMY_CONTEXT),
              existingCollections = rollingCollections.takeLast(MAX_BATCH_TAXONOMY_CONTEXT),
            )
            if (suggestion.tagNames.isEmpty() && suggestion.collectionNames.isEmpty()) {
              error("分類候補がありませんでした")
            }
            addDistinctOrganizationNames(rollingTags, suggestion.tagNames)
            addDistinctOrganizationNames(rollingCollections, suggestion.collectionNames)
            val draft = LibraryOrganizationBatchDraft(
              tagNames = suggestion.tagNames,
              collectionNames = suggestion.collectionNames,
            )
            _state.update { state ->
              state.copy(
                suggestions = state.suggestions + (key to suggestion),
                batch = state.batch.copy(
                  completed = index + 1,
                  currentBook = null,
                  drafts = state.batch.drafts + (key to draft),
                  selectedKeys = state.batch.selectedKeys + key,
                ),
              )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (error: Throwable) {
            _state.update { state ->
              state.copy(
                batch = state.batch.copy(
                  completed = index + 1,
                  currentBook = null,
                  failures = state.batch.failures +
                    (key to (error.message ?: "AI候補を生成できませんでした")),
                ),
              )
            }
          }
        }

        _state.update { state ->
          val generated = state.batch.drafts.size
          val failed = state.batch.failures.size
          state.copy(
            batch = state.batch.copy(running = false, currentBook = null),
            message = when {
              failed == 0 -> "$generated 冊のAI整理候補を生成しました"
              generated == 0 -> "AI整理候補を生成できませんでした"
              else -> "$generated 冊の候補を生成し、$failed 冊は失敗しました"
            },
          )
        }
      } catch (_: CancellationException) {
        _state.update { state ->
          state.copy(
            batch = state.batch.copy(running = false, currentBook = null),
            message = "一括AI解析を停止しました。生成済み候補はレビューできます",
          )
        }
      } finally {
        batchJob = null
      }
    }
  }

  fun cancelBatchSuggestion() {
    batchJob?.cancel()
  }

  fun toggleBatchSelection(key: LibraryBookKey) {
    _state.update { state ->
      if (key !in state.batch.drafts) return@update state
      val selected = if (key in state.batch.selectedKeys) {
        state.batch.selectedKeys - key
      } else {
        state.batch.selectedKeys + key
      }
      state.copy(batch = state.batch.copy(selectedKeys = selected))
    }
  }

  fun selectAllBatchCandidates() {
    _state.update { state ->
      state.copy(batch = state.batch.copy(selectedKeys = state.batch.drafts.keys))
    }
  }

  fun clearBatchSelection() {
    _state.update { state ->
      state.copy(batch = state.batch.copy(selectedKeys = emptySet()))
    }
  }

  fun updateBatchDraft(
    key: LibraryBookKey,
    draft: LibraryOrganizationBatchDraft,
  ) {
    _state.update { state ->
      if (key !in state.batch.drafts) return@update state
      state.copy(batch = state.batch.copy(drafts = state.batch.drafts + (key to draft)))
    }
  }

  fun clearBatchReview() {
    if (_state.value.batch.running || _state.value.batch.applying) return
    val batchKeys = _state.value.batch.drafts.keys
    _state.update { state ->
      state.copy(
        suggestions = state.suggestions - batchKeys,
        batch = LibraryOrganizationBatchUiState(),
      )
    }
  }

  fun applyBatch(books: List<LibraryBook>) {
    val state = _state.value
    if (state.batch.running || state.batch.applying) return
    val selectedKeys = state.batch.selectedKeys
    if (selectedKeys.isEmpty()) {
      _state.update { it.copy(message = "適用する候補を選択してください") }
      return
    }

    val booksByKey = books.associateBy(LibraryBook::organizationKey)
    val skippedKeys = linkedSetOf<LibraryBookKey>()
    val updates = selectedKeys.mapNotNull { key ->
      val book = booksByKey[key]
      val batchDraft = state.batch.drafts[key]
      if (book == null || batchDraft == null) {
        skippedKeys += key
        return@mapNotNull null
      }
      val currentOrganization = state.snapshot.organizationFor(book)
      if (currentOrganization.tags.isNotEmpty() || currentOrganization.collections.isNotEmpty()) {
        skippedKeys += key
        return@mapNotNull null
      }
      LibraryOrganizationUpdate(
        book = book,
        draft = LibraryOrganizationDraft(
          tagNames = batchDraft.tagNames,
          collectionNames = batchDraft.collectionNames,
          readingStatus = currentOrganization.readingStatus,
        ),
      )
    }

    if (updates.isEmpty()) {
      _state.update {
        it.copy(message = "選択した蔵書はすでに整理済みか、候補を利用できません")
      }
      return
    }

    viewModelScope.launch {
      _state.update { current -> current.copy(batch = current.batch.copy(applying = true)) }
      runCatching {
        repository.saveAll(updates)
        repository.snapshot()
      }.onSuccess { snapshot ->
        val appliedKeys = updates.mapTo(linkedSetOf()) { it.book.organizationKey() }
        _state.update { current ->
          val remainingDrafts = current.batch.drafts - appliedKeys - skippedKeys
          current.copy(
            snapshot = snapshot,
            suggestions = current.suggestions - appliedKeys - skippedKeys,
            batch = current.batch.copy(
              applying = false,
              drafts = remainingDrafts,
              selectedKeys = current.batch.selectedKeys - appliedKeys - skippedKeys,
              failures = current.batch.failures - appliedKeys - skippedKeys,
            ),
            message = if (skippedKeys.isEmpty()) {
              "${appliedKeys.size} 冊の整理候補を適用しました"
            } else {
              "${appliedKeys.size} 冊を適用し、${skippedKeys.size} 冊は手動変更済みのためスキップしました"
            },
          )
        }
      }.onFailure { error ->
        _state.update { current ->
          current.copy(
            batch = current.batch.copy(applying = false),
            message = error.message ?: "整理候補を一括適用できませんでした",
          )
        }
      }
    }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }

  override fun onCleared() {
    batchJob?.cancel()
    super.onCleared()
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

internal fun addDistinctOrganizationNames(
  destination: MutableList<String>,
  candidates: List<String>,
) {
  val normalized = destination.mapTo(linkedSetOf()) { it.trim().lowercase(Locale.ROOT) }
  candidates.forEach { candidate ->
    val cleaned = candidate.trim()
    if (cleaned.isEmpty()) return@forEach
    if (normalized.add(cleaned.lowercase(Locale.ROOT))) destination += cleaned
  }
}

private const val MAX_BATCH_TAXONOMY_CONTEXT = 100
