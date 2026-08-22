package dev.terashima.yomitorirss.feature.task

import java.time.LocalDate

class TaskChangeNotifyingRepository(
  private val delegate: TaskRepository,
  private val onChanged: () -> Unit,
) : TaskRepository {
  override suspend fun listTasks(): List<TaskItem> = delegate.listTasks()

  override suspend fun createTask(
    title: String,
    description: String,
    parentId: String?,
    dueDate: LocalDate?,
  ) {
    delegate.createTask(title, description, parentId, dueDate)
    onChanged()
  }

  override suspend fun updateTask(
    id: String,
    title: String,
    description: String,
    dueDate: LocalDate?,
  ) {
    delegate.updateTask(id, title, description, dueDate)
    onChanged()
  }

  override suspend fun deleteTask(id: String) {
    delegate.deleteTask(id)
    onChanged()
  }

  override suspend fun setCompleted(id: String, completed: Boolean) {
    delegate.setCompleted(id, completed)
    onChanged()
  }
}
