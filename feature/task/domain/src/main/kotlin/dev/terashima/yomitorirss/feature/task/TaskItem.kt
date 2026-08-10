package dev.terashima.yomitorirss.feature.task

import java.time.Instant
import java.time.LocalDate

data class TaskItem(
  val id: String,
  val title: String,
  val description: String = "",
  val parentId: String?,
  val dueDate: LocalDate?,
  val completedAt: Instant?,
  val createdAt: Instant,
  val sortOrder: Long,
) {
  val completed: Boolean get() = completedAt != null
}
