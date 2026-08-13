package dev.terashima.yomitorirss.feature.summary

enum class SummaryQueueTaskState {
  QUEUED,
  RUNNING,
  COMPLETED,
  FAILED,
  STOPPED,
  CANCELLED,
  UNKNOWN,
}

data class SummaryQueueTask(
  val articleId: String,
  val articleTitle: String,
  val sourceTitle: String,
  val state: SummaryQueueTaskState,
  val queuedAt: String,
  val startedAt: String?,
  val finishedAt: String?,
  val error: String?,
  val progressStage: String? = null,
  val progressCurrent: Int? = null,
  val progressTotal: Int? = null,
)

interface SummaryTaskQueueRepository {
  suspend fun listTasks(): List<SummaryQueueTask>
  suspend fun kick()
  suspend fun stop(articleId: String): Boolean
  suspend fun cancel(articleId: String): Boolean
  suspend fun resume(articleId: String): Boolean
}
