package dev.terashima.yomitorirss.feature.task

import java.time.LocalDate

interface TaskReader {
  suspend fun listTasks(): List<TaskItem>
}

interface TaskRepository : TaskReader {
  suspend fun createTask(title: String, description: String, parentId: String?, dueDate: LocalDate?)
  suspend fun updateTask(id: String, title: String, description: String, dueDate: LocalDate?)
  suspend fun deleteTask(id: String)
  suspend fun setCompleted(id: String, completed: Boolean)
}
