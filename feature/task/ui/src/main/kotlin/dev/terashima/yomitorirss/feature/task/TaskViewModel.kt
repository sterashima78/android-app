package dev.terashima.yomitorirss.feature.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskUiState(
  val initialized: Boolean = false,
  val tasks: List<TaskItem> = emptyList(),
  val filter: TaskFilter = TaskFilter.UNFINISHED,
  val expandedIds: Set<String> = emptySet(),
  val error: String? = null,
)

class TaskViewModel(
  private val repository: TaskRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(TaskUiState())
  val state: StateFlow<TaskUiState> = _state.asStateFlow()

  init { reload() }

  fun selectFilter(filter: TaskFilter) { _state.update { it.copy(filter = filter) } }

  fun toggleExpanded(taskId: String) {
    _state.update {
      it.copy(expandedIds = if (taskId in it.expandedIds) it.expandedIds - taskId else it.expandedIds + taskId)
    }
  }

  fun createTask(title: String, description: String, parentId: String?, dueDate: LocalDate?) = mutate {
    repository.createTask(title, description, parentId, dueDate)
  }

  fun updateTask(id: String, title: String, description: String, dueDate: LocalDate?) = mutate {
    repository.updateTask(id, title, description, dueDate)
  }

  fun deleteTask(id: String) = mutate { repository.deleteTask(id) }

  fun setCompleted(id: String, completed: Boolean) = mutate { repository.setCompleted(id, completed) }

  fun reload() { viewModelScope.launch(Dispatchers.IO) { loadTasks() } }

  private fun mutate(action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { action() }.onSuccess { loadTasks() }.onFailure(::showError)
    }
  }

  private suspend fun loadTasks() {
    runCatching { repository.listTasks() }
      .onSuccess { loaded ->
        val loadedIds = loaded.mapTo(mutableSetOf()) { it.id }
        _state.update { current ->
          current.copy(
            initialized = true,
            tasks = loaded,
            expandedIds = if (!current.initialized) loadedIds else current.expandedIds.intersect(loadedIds),
            error = null,
          )
        }
      }
      .onFailure(::showError)
  }

  private fun showError(error: Throwable) {
    _state.update { it.copy(initialized = true, error = error.message ?: "タスクの更新に失敗しました") }
  }

  class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(TaskViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return TaskViewModel(repository) as T
    }
  }
}
