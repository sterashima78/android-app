package dev.terashima.yomitorirss.feature.task.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.task.TaskRepository
import java.time.LocalDate

class DefaultTaskRepository(
  database: DatabaseConnection,
) : TaskRepository {
  private val store = TaskStore(database)

  override suspend fun listTasks(): List<TaskItem> = store.listTasks()

  override suspend fun createTask(title: String, description: String, parentId: String?, dueDate: LocalDate?) {
    store.createTask(title, description, parentId, dueDate)
  }

  override suspend fun updateTask(id: String, title: String, description: String, dueDate: LocalDate?) {
    store.updateTask(id, title, description, dueDate)
  }

  override suspend fun deleteTask(id: String) {
    store.deleteTask(id)
  }

  override suspend fun setCompleted(id: String, completed: Boolean) {
    store.setCompleted(id, completed)
  }
}
