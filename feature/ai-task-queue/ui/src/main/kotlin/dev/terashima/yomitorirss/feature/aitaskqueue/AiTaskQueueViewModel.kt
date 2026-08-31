package dev.terashima.yomitorirss.feature.aitaskqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AiTaskQueueUiState(
  val tasks: List<AiTaskQueueItem> = emptyList(),
  val taskCounts: AiTaskQueueCounts = AiTaskQueueCounts(),
  val loading: Boolean = true,
  val localPaused: Boolean = false,
  val cloudPaused: Boolean = false,
  val resumeLocalWhenCharging: Boolean = true,
  val actionError: String? = null,
)

class AiTaskQueueViewModel(
  private val repository: AiTaskQueueRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(AiTaskQueueUiState())
  val state: StateFlow<AiTaskQueueUiState> = _state.asStateFlow()
  private var pollingJob: Job? = null

  fun startObserving() {
    if (pollingJob?.isActive == true) return
    pollingJob = viewModelScope.launch(Dispatchers.IO) {
      reload()
      runCatching { repository.kick() }
        .onFailure(::showError)
      while (isActive) {
        delay(POLL_INTERVAL_MILLIS)
        reload()
      }
    }
  }

  fun stopObserving() {
    pollingJob?.cancel()
    pollingJob = null
  }

  fun setLocalPaused(paused: Boolean) {
    _state.update { it.copy(localPaused = paused, actionError = null) }
    runExecutionAction { repository.setLocalPaused(paused) }
  }

  fun setCloudPaused(paused: Boolean) {
    _state.update { it.copy(cloudPaused = paused, actionError = null) }
    runExecutionAction { repository.setCloudPaused(paused) }
  }

  fun setResumeLocalWhenCharging(enabled: Boolean) {
    _state.update { it.copy(resumeLocalWhenCharging = enabled, actionError = null) }
    runExecutionAction { repository.setResumeLocalWhenCharging(enabled) }
  }

  fun stop(taskId: String) = runTaskAction { repository.stop(taskId) }

  fun cancel(taskId: String) = runTaskAction { repository.cancel(taskId) }

  fun resume(taskId: String) = runTaskAction { repository.resume(taskId) }

  fun retryFailedBookmarkTasks() {
    _state.update { it.copy(actionError = null) }
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.retryFailedBookmarkTasks() }
        .onFailure(::showError)
      reload()
    }
  }

  private fun runExecutionAction(action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }
        .onFailure(::showError)
      reload()
    }
  }

  private fun runTaskAction(action: suspend () -> Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }
        .onSuccess { changed ->
          _state.update {
            it.copy(
              actionError = if (changed) null else "タスクの状態が変更されたため操作できませんでした",
            )
          }
        }
        .onFailure(::showError)
      reload()
    }
  }

  private suspend fun reload() {
    runCatching {
      val tasks = repository.listTasks()
      QueueSnapshot(
        tasks = tasks,
        taskCounts = countAiTaskQueueTasks(tasks),
        executionState = repository.executionState(),
      )
    }
      .onSuccess { snapshot ->
        _state.update {
          it.copy(
            tasks = prepareVisibleAiTasks(snapshot.tasks),
            taskCounts = snapshot.taskCounts,
            loading = false,
            localPaused = snapshot.executionState.localPaused,
            cloudPaused = snapshot.executionState.cloudPaused,
            resumeLocalWhenCharging = snapshot.executionState.resumeLocalWhenCharging,
          )
        }
      }
      .onFailure { error ->
        _state.update { it.copy(loading = false, actionError = error.userMessage()) }
      }
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(actionError = error.userMessage()) }
  }

  override fun onCleared() {
    stopObserving()
  }

  class Factory(
    private val repository: AiTaskQueueRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(AiTaskQueueViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return AiTaskQueueViewModel(repository) as T
    }
  }

  private data class QueueSnapshot(
    val tasks: List<AiTaskQueueItem>,
    val taskCounts: AiTaskQueueCounts,
    val executionState: AiTaskQueueExecutionState,
  )

  private companion object {
    const val POLL_INTERVAL_MILLIS = 1_000L
  }
}

internal fun countAiTaskQueueTasks(tasks: List<AiTaskQueueItem>): AiTaskQueueCounts = AiTaskQueueCounts(
  running = tasks.count { it.state == AiTaskQueueItemState.RUNNING },
  queued = tasks.count { it.state == AiTaskQueueItemState.QUEUED },
  pausedOrStopped = tasks.count {
    it.state == AiTaskQueueItemState.PAUSED || it.state == AiTaskQueueItemState.STOPPED
  },
)

internal fun prepareVisibleAiTasks(tasks: List<AiTaskQueueItem>): List<AiTaskQueueItem> =
  tasks
    .filterNot { it.state == AiTaskQueueItemState.COMPLETED }
    .sortedWith(
      compareBy<AiTaskQueueItem> { task ->
        when (task.state) {
          AiTaskQueueItemState.RUNNING -> 0
          AiTaskQueueItemState.QUEUED -> 1
          AiTaskQueueItemState.PAUSED -> 2
          else -> 3
        }
      }.thenBy { task ->
        when (task.priority) {
          AiTaskQueueItemPriority.HIGH -> 0
          AiTaskQueueItemPriority.NORMAL -> 1
          AiTaskQueueItemPriority.LOW -> 2
        }
      },
    )

private fun Throwable.userMessage(): String =
  message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
