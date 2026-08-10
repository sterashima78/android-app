package dev.terashima.yomitorirss.feature.task

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTreeTest {
  private val today = LocalDate.of(2026, 8, 7)

  @Test
  fun `期日超過は未完了かつ期日が過去のタスクだけを数える`() {
    val tasks = listOf(
      task("overdue", dueDate = today.minusDays(1)),
      task("today", dueDate = today),
      task("done", dueDate = today.minusDays(2), completed = true),
    )

    assertEquals(1, taskCount(tasks, TaskFilter.OVERDUE, today))
    assertEquals(TaskStatus.OVERDUE, taskStatus(tasks[0], today))
    assertEquals(TaskStatus.UNFINISHED, taskStatus(tasks[1], today))
    assertEquals(TaskStatus.COMPLETED, taskStatus(tasks[2], today))
  }

  @Test
  fun `子タスクがフィルタ条件に一致すると親も階層表示のため残す`() {
    val parent = task("parent", completed = true)
    val child = task("child", parentId = parent.id, dueDate = today.minusDays(1))

    val rows = taskTreeRows(
      tasks = listOf(parent, child),
      filter = TaskFilter.OVERDUE,
      expandedIds = setOf(parent.id),
      today = today,
    )

    assertEquals(listOf(parent.id, child.id), rows.map { it.task.id })
    assertEquals(listOf(0, 1), rows.map { it.depth })
  }

  @Test
  fun `折りたたんだ親の子タスクは表示しない`() {
    val parent = task("parent")
    val child = task("child", parentId = parent.id)

    val rows = taskTreeRows(
      tasks = listOf(parent, child),
      filter = TaskFilter.ALL,
      expandedIds = emptySet(),
      today = today,
    )

    assertEquals(listOf(parent.id), rows.map { it.task.id })
  }

  private fun task(
    id: String,
    parentId: String? = null,
    dueDate: LocalDate? = null,
    completed: Boolean = false,
  ) = TaskItem(
    id = id,
    title = id,
    parentId = parentId,
    dueDate = dueDate,
    completedAt = if (completed) Instant.parse("2026-08-01T00:00:00Z") else null,
    createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    sortOrder = 0,
  )
}
