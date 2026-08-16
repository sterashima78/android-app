package dev.terashima.yomitorirss.feature.settings

enum class AiTaskQueueItemKind {
  SUMMARY,
  LIBRARY_ORGANIZATION,
}

enum class AiTaskQueueItemState {
  QUEUED,
  RUNNING,
  PAUSED,
  COMPLETED,
  FAILED,
  STOPPED,
  CANCELLED,
  UNKNOWN,
}

data class AiTaskQueueItem(
  val id: String,
  val kind: AiTaskQueueItemKind,
  val title: String,
  val source: String,
  val state: AiTaskQueueItemState,
  val progressCurrent: Int? = null,
  val progressTotal: Int? = null,
  val pendingReviewCount: Int? = null,
  val error: String? = null,
  val canStop: Boolean = false,
  val canCancel: Boolean = false,
  val canResume: Boolean = false,
)

data class AiTaskQueueExecutionState(
  val paused: Boolean,
  val resumeWhenCharging: Boolean,
)

interface AiTaskQueueRepository {
  suspend fun listTasks(): List<AiTaskQueueItem>
  suspend fun executionState(): AiTaskQueueExecutionState
  suspend fun kick()
  suspend fun setPaused(paused: Boolean)
  suspend fun setResumeWhenCharging(enabled: Boolean)
  suspend fun stop(taskId: String): Boolean
  suspend fun cancel(taskId: String): Boolean
  suspend fun resume(taskId: String): Boolean
}
