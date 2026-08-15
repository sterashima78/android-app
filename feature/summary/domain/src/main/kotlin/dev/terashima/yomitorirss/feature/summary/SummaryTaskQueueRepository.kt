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

enum class SummaryQueueTaskProgressStage {
  FETCHING_ARTICLE,
  PREPARING_MODEL,
  GENERATING_SUMMARY,
  SUMMARIZING_CHUNK,
  REDUCING_SUMMARY,
  FINALIZING_SUMMARY,
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
  val progressStage: SummaryQueueTaskProgressStage? = null,
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
