package dev.terashima.yomitorirss.feature.settings

enum class AiTaskQueueItemKind {
  SUMMARY,
  LIBRARY_ORGANIZATION,
  KNOWLEDGE_WIKI,
}

enum class AiTaskQueueItemPriority {
  HIGH,
  NORMAL,
  LOW,
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

enum class AiTaskQueueProgressStage {
  FETCHING_CONTENT,
  PREPARING_MODEL,
  GENERATING,
  PROCESSING_CHUNK,
  REDUCING,
  FINALIZING,
  UNKNOWN,
}

data class AiTaskQueueItem(
  val id: String,
  val kind: AiTaskQueueItemKind,
  val title: String,
  val source: String,
  val state: AiTaskQueueItemState,
  val priority: AiTaskQueueItemPriority = AiTaskQueueItemPriority.NORMAL,
  val progressStage: AiTaskQueueProgressStage? = null,
  val progressCurrent: Int? = null,
  val progressTotal: Int? = null,
  val pendingReviewCount: Int? = null,
  val error: String? = null,
  val canStop: Boolean = false,
  val canCancel: Boolean = false,
  val canResume: Boolean = false,
)

data class AiTaskQueueCounts(
  val running: Int = 0,
  val queued: Int = 0,
  val pausedOrStopped: Int = 0,
)

data class AiTaskQueueExecutionState(
  val paused: Boolean,
  val resumeWhenCharging: Boolean,
)

interface AiTaskQueueRepository {
  suspend fun listTasks(): List<AiTaskQueueItem>

  suspend fun taskCounts(): AiTaskQueueCounts {
    val tasks = listTasks()
    return AiTaskQueueCounts(
      running = tasks.count { it.state == AiTaskQueueItemState.RUNNING },
      queued = tasks.count { it.state == AiTaskQueueItemState.QUEUED },
      pausedOrStopped = tasks.count {
        it.state == AiTaskQueueItemState.PAUSED || it.state == AiTaskQueueItemState.STOPPED
      },
    )
  }

  suspend fun executionState(): AiTaskQueueExecutionState
  suspend fun kick()
  suspend fun setPaused(paused: Boolean)
  suspend fun setResumeWhenCharging(enabled: Boolean)
  suspend fun stop(taskId: String): Boolean
  suspend fun cancel(taskId: String): Boolean
  suspend fun resume(taskId: String): Boolean
  suspend fun retryFailedBookmarkTasks(): Int = 0
}
