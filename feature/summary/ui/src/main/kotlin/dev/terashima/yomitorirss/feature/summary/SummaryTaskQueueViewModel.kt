package dev.terashima.yomitorirss.feature.summary

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

data class SummaryTaskQueueUiState(
  val tasks: List<SummaryQueueTask> = emptyList(),
  val loading: Boolean = true,
  val queuePaused: Boolean = false,
  val resumeWhenCharging: Boolean = true,
  val actionError: String? = null,
)

class SummaryTaskQueueViewModel(
  private val repository: SummaryTaskQueueRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(SummaryTaskQueueUiState())
  val state: StateFlow<SummaryTaskQueueUiState> = _state.asStateFlow()
  private var pollingJob: Job? = null

  fun startObserving() {
    if (pollingJob?.isActive == true) return
    pollingJob = viewModelScope.launch(Dispatchers.IO) {
      runCatching { repository.kick() }
        .onFailure(::showError)
      while (isActive) {
        reload()
        delay(POLL_INTERVAL_MILLIS)
      }
    }
  }

  fun stopObserving() {
    pollingJob?.cancel()
    pollingJob = null
  }

  fun setPaused(paused: Boolean) {
    _state.update { it.copy(queuePaused = paused, actionError = null) }
    runExecutionAction { repository.setPaused(paused) }
  }

  fun setResumeWhenCharging(enabled: Boolean) {
    _state.update { it.copy(resumeWhenCharging = enabled, actionError = null) }
    runExecutionAction { repository.setResumeWhenCharging(enabled) }
  }

  fun stop(articleId: String) = runTaskAction { repository.stop(articleId) }

  fun cancel(articleId: String) = runTaskAction { repository.cancel(articleId) }

  fun resume(articleId: String) = runTaskAction { repository.resume(articleId) }

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
      QueueSnapshot(
        tasks = repository.listTasks(),
        executionState = repository.executionState(),
      )
    }
      .onSuccess { snapshot ->
        _state.update {
          it.copy(
            tasks = snapshot.tasks,
            loading = false,
            queuePaused = snapshot.executionState.paused,
            resumeWhenCharging = snapshot.executionState.resumeWhenCharging,
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
    private val repository: SummaryTaskQueueRepository,
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(SummaryTaskQueueViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return SummaryTaskQueueViewModel(repository) as T
    }
  }

  private data class QueueSnapshot(
    val tasks: List<SummaryQueueTask>,
    val executionState: SummaryQueueExecutionState,
  )

  private companion object {
    const val POLL_INTERVAL_MILLIS = 1_000L
  }
}

private fun Throwable.userMessage(): String =
  message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
