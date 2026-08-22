package dev.terashima.yomitorirss.feature.task

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskChangeNotifyingRepositoryTest {
  @Test
  fun `読み取りでは変更通知を発火しない`() = runBlocking {
    var notifications = 0
    val repository = TaskChangeNotifyingRepository(FakeTaskRepository()) { notifications += 1 }

    repository.listTasks()

    assertEquals(0, notifications)
  }

  @Test
  fun `各変更の成功後に変更通知を発火する`() = runBlocking {
    var notifications = 0
    val repository = TaskChangeNotifyingRepository(FakeTaskRepository()) { notifications += 1 }

    repository.createTask("task", "", null, null)
    repository.updateTask("task", "updated", "", LocalDate.of(2026, 8, 23))
    repository.setCompleted("task", true)
    repository.deleteTask("task")

    assertEquals(4, notifications)
  }

  @Test
  fun `変更が失敗した場合は変更通知を発火しない`() = runBlocking {
    var notifications = 0
    val delegate = FakeTaskRepository(failMutations = true)
    val repository = TaskChangeNotifyingRepository(delegate) { notifications += 1 }

    val result = runCatching { repository.deleteTask("task") }

    assertTrue(result.isFailure)
    assertEquals(0, notifications)
  }

  private class FakeTaskRepository(
    private val failMutations: Boolean = false,
  ) : TaskRepository {
    override suspend fun listTasks(): List<TaskItem> = emptyList()

    override suspend fun createTask(title: String, description: String, parentId: String?, dueDate: LocalDate?) {
      mutate()
    }

    override suspend fun updateTask(id: String, title: String, description: String, dueDate: LocalDate?) {
      mutate()
    }

    override suspend fun deleteTask(id: String) {
      mutate()
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
      mutate()
    }

    private fun mutate() {
      if (failMutations) error("mutation failed")
    }
  }
}
