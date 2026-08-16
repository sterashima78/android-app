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

enum class SummaryQueueTaskPriority {
  HIGH,
  NORMAL,
  LOW,
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
  val priority: SummaryQueueTaskPriority = SummaryQueueTaskPriority.NORMAL,
  val progressStage: SummaryQueueTaskProgressStage? = null,
  val progressCurrent: Int? = null,
  val progressTotal: Int? = null,
)

data class SummaryQueueTaskCounts(
  val queued: Int = 0,
  val running: Int = 0,
  val stopped: Int = 0,
)

data class SummaryQueueExecutionState(
  val paused: Boolean,
  val resumeWhenCharging: Boolean,
)

interface SummaryTaskQueueRepository {
  suspend fun listTasks(): List<SummaryQueueTask>

  suspend fun taskCounts(): SummaryQueueTaskCounts {
    val tasks = listTasks()
    return SummaryQueueTaskCounts(
      queued = tasks.count { it.state == SummaryQueueTaskState.QUEUED },
      running = tasks.count { it.state == SummaryQueueTaskState.RUNNING },
      stopped = tasks.count { it.state == SummaryQueueTaskState.STOPPED },
    )
  }

  suspend fun executionState(): SummaryQueueExecutionState
  suspend fun kick()
  suspend fun setPaused(paused: Boolean)
  suspend fun setResumeWhenCharging(enabled: Boolean)
  suspend fun stop(articleId: String): Boolean
  suspend fun cancel(articleId: String): Boolean
  suspend fun resume(articleId: String): Boolean
}
