package dev.terashima.yomitorirss.feature.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnowledgeUiState(
  val initialized: Boolean = false,
  val pages: List<KnowledgePageSummary> = emptyList(),
  val query: String = "",
  val selectedPage: KnowledgePage? = null,
  val building: Boolean = false,
  val working: Boolean = false,
  val composerOpen: Boolean = false,
  val composerRequest: String = "",
  val composerSourcePageId: String? = null,
  val editInstruction: String = "",
  val deleteConfirmationOpen: Boolean = false,
  val splitDialogOpen: Boolean = false,
  val mergeDialogOpen: Boolean = false,
  val mergeCandidates: List<KnowledgePageSummary> = emptyList(),
  val lastBuild: KnowledgeBuildResult? = null,
  val message: String? = null,
)

class KnowledgeViewModel(
  private val repository: KnowledgeRepository,
  private val builder: KnowledgeBuilder,
  private val creator: KnowledgePageCreator,
  private val editor: KnowledgePageEditor,
  private val scheduleRebuild: (() -> Unit)? = null,
) : ViewModel() {
  private val _state = MutableStateFlow(KnowledgeUiState())
  val state: StateFlow<KnowledgeUiState> = _state.asStateFlow()

  init {
    refresh()
    viewModelScope.launch {
      repository.changes.drop(1).collect {
        refreshAfterDataChange()
      }
    }
  }

  fun updateQuery(query: String) {
    _state.update { it.copy(query = query) }
    refresh()
  }

  fun openPage(id: String) {
    viewModelScope.launch {
      runCatching { repository.findPage(id) }
        .onSuccess { page ->
          _state.update {
            it.copy(
              selectedPage = page,
              editInstruction = "",
              deleteConfirmationOpen = false,
              splitDialogOpen = false,
              mergeDialogOpen = false,
              mergeCandidates = emptyList(),
              message = null,
            )
          }
        }
        .onFailure(::reportError)
    }
  }

  fun closePage() {
    _state.update {
      it.copy(
        selectedPage = null,
        editInstruction = "",
        deleteConfirmationOpen = false,
        splitDialogOpen = false,
        mergeDialogOpen = false,
        mergeCandidates = emptyList(),
        message = null,
      )
    }
  }

  fun startCreate(sourcePageId: String? = null) {
    if (_state.value.working) return
    _state.update {
      it.copy(
        composerOpen = true,
        composerRequest = "",
        composerSourcePageId = sourcePageId,
        message = null,
      )
    }
  }

  fun cancelCreate() {
    if (_state.value.working) return
    _state.update {
      it.copy(
        composerOpen = false,
        composerRequest = "",
        composerSourcePageId = null,
        message = null,
      )
    }
  }

  fun updateComposerRequest(request: String) {
    _state.update { it.copy(composerRequest = request) }
  }

  fun createPage() {
    val current = _state.value
    val request = current.composerRequest.trim()
    if (current.working) return
    if (request.isBlank()) {
      _state.update { it.copy(message = "作成したい記事の内容を入力してください") }
      return
    }
    _state.update { it.copy(working = true, message = null) }
    viewModelScope.launch {
      runCatching { creator.createPage(request, current.composerSourcePageId) }
        .onSuccess { page ->
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = page,
              working = false,
              composerOpen = false,
              composerRequest = "",
              composerSourcePageId = null,
              editInstruction = "",
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(working = false) }
          reportError(error)
        }
    }
  }

  fun updateEditInstruction(instruction: String) {
    _state.update { it.copy(editInstruction = instruction) }
  }

  fun editPage() {
    val current = _state.value
    val page = current.selectedPage ?: return
    val instruction = current.editInstruction.trim()
    if (current.working) return
    if (instruction.isBlank()) {
      _state.update { it.copy(message = "編集内容を入力してください") }
      return
    }
    _state.update { it.copy(working = true, message = null) }
    viewModelScope.launch {
      runCatching { editor.editPage(page.id, instruction) }
        .onSuccess { updatedPage ->
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = updatedPage,
              working = false,
              editInstruction = "",
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(working = false) }
          reportError(error)
        }
    }
  }

  fun startDelete() {
    if (_state.value.working || _state.value.selectedPage == null) return
    _state.update { it.copy(deleteConfirmationOpen = true, message = null) }
  }

  fun cancelDelete() {
    if (_state.value.working) return
    _state.update { it.copy(deleteConfirmationOpen = false) }
  }

  fun deletePage() {
    val page = _state.value.selectedPage ?: return
    if (_state.value.working) return
    _state.update { it.copy(working = true, message = null) }
    viewModelScope.launch {
      runCatching { repository.deletePage(page.id) }
        .onSuccess {
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = null,
              working = false,
              deleteConfirmationOpen = false,
              splitDialogOpen = false,
              mergeDialogOpen = false,
              mergeCandidates = emptyList(),
              editInstruction = "",
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(working = false) }
          reportError(error)
        }
    }
  }

  fun startSplit() {
    if (_state.value.working || _state.value.selectedPage == null) return
    _state.update { it.copy(splitDialogOpen = true, message = null) }
  }

  fun cancelSplit() {
    if (_state.value.working) return
    _state.update { it.copy(splitDialogOpen = false) }
  }

  fun splitPage(heading: String) {
    val page = _state.value.selectedPage ?: return
    if (_state.value.working) return
    _state.update { it.copy(working = true, message = null) }
    viewModelScope.launch {
      runCatching { repository.splitPage(page.id, heading) }
        .onSuccess { updatedPage ->
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = updatedPage,
              working = false,
              splitDialogOpen = false,
              editInstruction = "",
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(working = false) }
          reportError(error)
        }
    }
  }

  fun startMerge() {
    val selectedId = _state.value.selectedPage?.id ?: return
    if (_state.value.working) return
    viewModelScope.launch {
      runCatching { repository.listPages("").filterNot { it.id == selectedId } }
        .onSuccess { candidates ->
          if (candidates.isEmpty()) {
            _state.update { it.copy(message = "統合できる別の記事がありません") }
          } else {
            _state.update {
              it.copy(
                mergeDialogOpen = true,
                mergeCandidates = candidates,
                message = null,
              )
            }
          }
        }
        .onFailure(::reportError)
    }
  }

  fun cancelMerge() {
    if (_state.value.working) return
    _state.update { it.copy(mergeDialogOpen = false, mergeCandidates = emptyList()) }
  }

  fun mergePage(secondaryId: String) {
    val primary = _state.value.selectedPage ?: return
    if (_state.value.working) return
    _state.update { it.copy(working = true, message = null) }
    viewModelScope.launch {
      runCatching { repository.mergePages(primary.id, secondaryId) }
        .onSuccess { mergedPage ->
          val pages = repository.listPages(_state.value.query)
          _state.update {
            it.copy(
              initialized = true,
              pages = pages,
              selectedPage = mergedPage,
              working = false,
              mergeDialogOpen = false,
              mergeCandidates = emptyList(),
              editInstruction = "",
              message = null,
            )
          }
        }
        .onFailure { error ->
          _state.update { it.copy(working = false) }
          reportError(error)
        }
    }
  }

  fun rebuild() {
    if (_state.value.building || _state.value.working) return
    val scheduler = scheduleRebuild
    if (scheduler != null) {
      _state.update { it.copy(message = null) }
      runCatching(scheduler).onFailure(::reportError)
      return
    }

    _state.update { it.copy(building = true, message = null) }
    viewModelScope.launch {
      runCatching { builder.rebuild() }
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

  private suspend fun refreshAfterDataChange() {
    val snapshot = _state.value
    val query = snapshot.query
    val selectedId = snapshot.selectedPage?.id
    runCatching {
      val pages = repository.listPages(query)
      val selected = selectedId?.let { repository.findPage(it) }
      pages to selected
    }.onSuccess { (pages, selected) ->
      _state.update { current ->
        if (current.query != query || current.selectedPage?.id != selectedId) {
          current
        } else {
          current.copy(
            initialized = true,
            pages = pages,
            selectedPage = selected,
            editInstruction = if (selected == null) "" else current.editInstruction,
          )
        }
      }
    }.onFailure(::reportError)
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
    private val builder: KnowledgeBuilder,
    private val creator: KnowledgePageCreator,
    private val editor: KnowledgePageEditor,
    private val scheduleRebuild: (() -> Unit)? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(KnowledgeViewModel::class.java))
      return KnowledgeViewModel(
        repository = repository,
        builder = builder,
        creator = creator,
        editor = editor,
        scheduleRebuild = scheduleRebuild,
      ) as T
    }
  }
}
