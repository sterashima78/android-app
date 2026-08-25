package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskExecutionProvider
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemPriority
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskPriority
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

internal class SummaryTaskQueueAdapter(
  private val repository: SummaryTaskQueueRepository,
) {
  suspend fun tasks(): List<AiTaskQueueItem> = repository.listTasks().map(::toAiTaskQueueItem)

  suspend fun executionState(): SummaryQueueExecutionState = repository.executionState()

  suspend fun kick() = repository.kick()

  suspend fun setLocalPaused(paused: Boolean) = repository.setLocalPaused(paused)

  suspend fun setCloudPaused(paused: Boolean) = repository.setCloudPaused(paused)

  suspend fun setResumeLocalWhenCharging(enabled: Boolean) = repository.setResumeLocalWhenCharging(enabled)

  suspend fun stop(taskId: String): Boolean? =
    taskId.takeIf(::handles)?.let { repository.stop(it.removePrefix(PREFIX)) }

  suspend fun cancel(taskId: String): Boolean? =
    taskId.takeIf(::handles)?.let { repository.cancel(it.removePrefix(PREFIX)) }

  suspend fun resume(taskId: String): Boolean? =
    taskId.takeIf(::handles)?.let { repository.resume(it.removePrefix(PREFIX)) }

  suspend fun retryFailedBookmarkTasks(): Int = repository.retryFailedBookmarkTasks()

  private fun handles(taskId: String): Boolean = taskId.startsWith(PREFIX)

  private fun toAiTaskQueueItem(task: SummaryQueueTask): AiTaskQueueItem = AiTaskQueueItem(
    id = "$PREFIX${task.articleId}",
    kind = AiTaskQueueItemKind.SUMMARY,
    title = task.articleTitle,
    source = task.sourceTitle,
    state = task.state.toAiTaskState(),
    priority = task.priority.toAiTaskPriority(),
    progressStage = task.progressStage?.toAiTaskProgressStage(),
    progressCurrent = task.progressCurrent,
    progressTotal = task.progressTotal,
    error = task.error,
    canStop = task.state == SummaryQueueTaskState.QUEUED || task.state == SummaryQueueTaskState.RUNNING,
    canCancel = task.state == SummaryQueueTaskState.QUEUED ||
      task.state == SummaryQueueTaskState.RUNNING ||
      task.state == SummaryQueueTaskState.STOPPED,
    canResume = task.state == SummaryQueueTaskState.STOPPED || task.state == SummaryQueueTaskState.FAILED,
    executionProvider = task.executionProvider.toAiTaskExecutionProvider(),
  )

  private fun SummaryQueueTaskPriority.toAiTaskPriority(): AiTaskQueueItemPriority = when (this) {
    SummaryQueueTaskPriority.HIGH -> AiTaskQueueItemPriority.HIGH
    SummaryQueueTaskPriority.NORMAL -> AiTaskQueueItemPriority.NORMAL
    SummaryQueueTaskPriority.LOW -> AiTaskQueueItemPriority.LOW
  }

  private fun SummaryQueueTaskState.toAiTaskState(): AiTaskQueueItemState = when (this) {
    SummaryQueueTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
    SummaryQueueTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
    SummaryQueueTaskState.COMPLETED -> AiTaskQueueItemState.COMPLETED
    SummaryQueueTaskState.FAILED -> AiTaskQueueItemState.FAILED
    SummaryQueueTaskState.STOPPED -> AiTaskQueueItemState.STOPPED
    SummaryQueueTaskState.CANCELLED -> AiTaskQueueItemState.CANCELLED
    SummaryQueueTaskState.UNKNOWN -> AiTaskQueueItemState.UNKNOWN
  }

  private fun SummaryQueueTaskProgressStage.toAiTaskProgressStage(): AiTaskQueueProgressStage = when (this) {
    SummaryQueueTaskProgressStage.FETCHING_ARTICLE -> AiTaskQueueProgressStage.FETCHING_CONTENT
    SummaryQueueTaskProgressStage.PREPARING_MODEL -> AiTaskQueueProgressStage.PREPARING_MODEL
    SummaryQueueTaskProgressStage.GENERATING_SUMMARY -> AiTaskQueueProgressStage.GENERATING
    SummaryQueueTaskProgressStage.SUMMARIZING_CHUNK -> AiTaskQueueProgressStage.PROCESSING_CHUNK
    SummaryQueueTaskProgressStage.REDUCING_SUMMARY -> AiTaskQueueProgressStage.REDUCING
    SummaryQueueTaskProgressStage.FINALIZING_SUMMARY -> AiTaskQueueProgressStage.FINALIZING
    SummaryQueueTaskProgressStage.CLOUD_GENERATING_SUMMARY -> AiTaskQueueProgressStage.CLOUD_GENERATING
    SummaryQueueTaskProgressStage.CLOUD_GENERATING_METADATA -> AiTaskQueueProgressStage.CLOUD_ENRICHING
    SummaryQueueTaskProgressStage.UNKNOWN -> AiTaskQueueProgressStage.UNKNOWN
  }

  private fun SummaryExecutionProvider.toAiTaskExecutionProvider(): AiTaskExecutionProvider = when (this) {
    SummaryExecutionProvider.LOCAL -> AiTaskExecutionProvider.LOCAL
    SummaryExecutionProvider.CHATGPT -> AiTaskExecutionProvider.CHATGPT
  }

  private companion object {
    const val PREFIX = "summary:"
  }
}
