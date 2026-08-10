package dev.terashima.yomitorirss.feature.task

import java.time.LocalDate

enum class TaskFilter(val label: String) {
  UNFINISHED("未完了"),
  COMPLETED("完了"),
  OVERDUE("期日超過"),
  ALL("すべて"),
}

enum class TaskStatus {
  UNFINISHED,
  COMPLETED,
  OVERDUE,
}

data class TaskTreeRow(
  val task: TaskItem,
  val depth: Int,
  val hasChildren: Boolean,
)

fun taskStatus(task: TaskItem, today: LocalDate = LocalDate.now()): TaskStatus = when {
  task.completed -> TaskStatus.COMPLETED
  task.dueDate != null && task.dueDate.isBefore(today) -> TaskStatus.OVERDUE
  else -> TaskStatus.UNFINISHED
}

fun taskCount(tasks: List<TaskItem>, filter: TaskFilter, today: LocalDate = LocalDate.now()): Int =
  tasks.count { it.matches(filter, today) }

fun taskTreeRows(
  tasks: List<TaskItem>,
  filter: TaskFilter,
  expandedIds: Set<String>,
  today: LocalDate = LocalDate.now(),
): List<TaskTreeRow> {
  if (tasks.isEmpty()) return emptyList()
  val byId = tasks.associateBy { it.id }
  val children = tasks.groupBy { it.parentId }.mapValues { (_, value) -> value.sortedWith(taskComparator) }
  val visibleIds = if (filter == TaskFilter.ALL) {
    tasks.mapTo(mutableSetOf()) { it.id }
  } else {
    val visible = mutableSetOf<String>()
    tasks.filter { it.matches(filter, today) }.forEach { matching ->
      var current: TaskItem? = matching
      val seen = mutableSetOf<String>()
      while (current != null && seen.add(current.id)) {
        visible += current.id
        current = current.parentId?.let(byId::get)
      }
    }
    visible
  }

  val rows = mutableListOf<TaskTreeRow>()
  val visited = mutableSetOf<String>()

  fun append(task: TaskItem, depth: Int) {
    if (!visited.add(task.id) || task.id !in visibleIds) return
    val taskChildren = children[task.id].orEmpty().filter { it.id in visibleIds }
    rows += TaskTreeRow(task, depth, taskChildren.isNotEmpty())
    if (task.id in expandedIds) taskChildren.forEach { append(it, depth + 1) }
  }

  val roots = tasks
    .filter { it.parentId == null || byId[it.parentId] == null }
    .sortedWith(taskComparator)
  roots.forEach { append(it, 0) }
  return rows
}

private fun TaskItem.matches(filter: TaskFilter, today: LocalDate): Boolean = when (filter) {
  TaskFilter.UNFINISHED -> !completed
  TaskFilter.COMPLETED -> completed
  TaskFilter.OVERDUE -> taskStatus(this, today) == TaskStatus.OVERDUE
  TaskFilter.ALL -> true
}

private val taskComparator = compareBy<TaskItem>({ it.sortOrder }, { it.createdAt }, { it.title })
