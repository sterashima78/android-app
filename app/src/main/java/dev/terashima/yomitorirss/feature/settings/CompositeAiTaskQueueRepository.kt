package dev.terashima.yomitorirss.feature.settings

import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

internal class CompositeAiTaskQueueRepository(
  private val summaryRepository: SummaryTaskQueueRepository,
  private val libraryRepository: LibraryOrganizationRepository,
  private val libraryScheduler: LibraryOrganizationBatchScheduler,
) : AiTaskQueueRepository {
  override suspend fun listTasks(): List<AiTaskQueueItem> {
    val libraryTask = libraryRepository.batchSnapshot()?.let(::toAiTaskQueueItem)
    val summaryTasks = summaryRepository.listTasks().map(::toAiTaskQueueItem)
    return listOfNotNull(libraryTask) + summaryTasks
  }

  override suspend fun executionState(): AiTaskQueueExecutionState =
    summaryRepository.executionState().let { state ->
      AiTaskQueueExecutionState(
        paused = state.paused,
        resumeWhenCharging = state.resumeWhenCharging,
      )
    }

  override suspend fun kick() {
    summaryRepository.kick()
    if (libraryRepository.batchSnapshot()?.status == LibraryOrganizationBatchStatus.RUNNING) {
      libraryScheduler.kick()
    }
  }

  override suspend fun setPaused(paused: Boolean) {
    if (paused) {
      summaryRepository.setPaused(true)
      try {
        when (libraryRepository.batchSnapshot()?.status) {
          LibraryOrganizationBatchStatus.RUNNING -> {
            libraryRepository.pauseBatch()
            libraryScheduler.cancel()
          }
          LibraryOrganizationBatchStatus.PAUSED -> libraryScheduler.cancel()
          LibraryOrganizationBatchStatus.COMPLETED,
          null -> Unit
        }
      } catch (error: Throwable) {
        runCatching { summaryRepository.setPaused(false) }
        throw error
      }
      return
    }

    summaryRepository.setPaused(false)
    try {
      when (libraryRepository.batchSnapshot()?.status) {
        LibraryOrganizationBatchStatus.PAUSED -> {
          libraryRepository.resumeBatch()
          libraryScheduler.kick()
        }
        LibraryOrganizationBatchStatus.RUNNING -> libraryScheduler.kick()
        LibraryOrganizationBatchStatus.COMPLETED,
        null -> Unit
      }
    } catch (error: Throwable) {
      runCatching { summaryRepository.setPaused(true) }
      throw error
    }
  }

  override suspend fun setResumeWhenCharging(enabled: Boolean) {
    summaryRepository.setResumeWhenCharging(enabled)
    if (enabled && libraryRepository.batchSnapshot()?.status == LibraryOrganizationBatchStatus.PAUSED) {
      // Re-running cancel does not change durable task state. It only ensures the library feature's
      // charging-constrained resume worker is registered with the newly enabled shared policy.
      libraryScheduler.cancel()
    }
  }

  override suspend fun stop(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.stop(taskId.removePrefix(SUMMARY_PREFIX))
    taskId.startsWith(LIBRARY_PREFIX) -> pauseLibraryTask(taskId)
    else -> false
  }

  override suspend fun cancel(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.cancel(taskId.removePrefix(SUMMARY_PREFIX))
    else -> false
  }

  override suspend fun resume(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.resume(taskId.removePrefix(SUMMARY_PREFIX))
    taskId.startsWith(LIBRARY_PREFIX) -> resumeLibraryTask(taskId)
    else -> false
  }

  private suspend fun pauseLibraryTask(taskId: String): Boolean {
    val batch = libraryRepository.batchSnapshot() ?: return false
    if (taskId != "$LIBRARY_PREFIX${batch.batchId}") return false
    if (batch.status != LibraryOrganizationBatchStatus.RUNNING) return false
    libraryRepository.pauseBatch()
    libraryScheduler.cancel()
    return true
  }

  private suspend fun resumeLibraryTask(taskId: String): Boolean {
    val batch = libraryRepository.batchSnapshot() ?: return false
    if (taskId != "$LIBRARY_PREFIX${batch.batchId}") return false
    if (batch.status != LibraryOrganizationBatchStatus.PAUSED) return false
    libraryRepository.resumeBatch()
    libraryScheduler.kick()
    return true
  }

  private fun toAiTaskQueueItem(task: SummaryQueueTask): AiTaskQueueItem = AiTaskQueueItem(
    id = "$SUMMARY_PREFIX${task.articleId}",
    kind = AiTaskQueueItemKind.SUMMARY,
    title = task.articleTitle,
    source = task.sourceTitle,
    state = task.state.toAiTaskState(),
    progressCurrent = task.progressCurrent,
    progressTotal = task.progressTotal,
    error = task.error,
    canStop = task.state == SummaryQueueTaskState.QUEUED || task.state == SummaryQueueTaskState.RUNNING,
    canCancel = task.state == SummaryQueueTaskState.QUEUED ||
      task.state == SummaryQueueTaskState.RUNNING ||
      task.state == SummaryQueueTaskState.STOPPED,
    canResume = task.state == SummaryQueueTaskState.STOPPED || task.state == SummaryQueueTaskState.FAILED,
  )

  private fun toAiTaskQueueItem(
    batch: dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchSnapshot,
  ): AiTaskQueueItem = AiTaskQueueItem(
    id = "$LIBRARY_PREFIX${batch.batchId}",
    kind = AiTaskQueueItemKind.LIBRARY_ORGANIZATION,
    title = "",
    source = "",
    state = when (batch.status) {
      LibraryOrganizationBatchStatus.RUNNING -> AiTaskQueueItemState.RUNNING
      LibraryOrganizationBatchStatus.PAUSED -> AiTaskQueueItemState.PAUSED
      LibraryOrganizationBatchStatus.COMPLETED -> AiTaskQueueItemState.COMPLETED
    },
    progressCurrent = batch.processed,
    progressTotal = batch.total,
    pendingReviewCount = batch.pendingReview + batch.deferred,
    canStop = batch.status == LibraryOrganizationBatchStatus.RUNNING,
    canResume = batch.status == LibraryOrganizationBatchStatus.PAUSED,
  )

  private fun SummaryQueueTaskState.toAiTaskState(): AiTaskQueueItemState = when (this) {
    SummaryQueueTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
    SummaryQueueTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
    SummaryQueueTaskState.COMPLETED -> AiTaskQueueItemState.COMPLETED
    SummaryQueueTaskState.FAILED -> AiTaskQueueItemState.FAILED
    SummaryQueueTaskState.STOPPED -> AiTaskQueueItemState.STOPPED
    SummaryQueueTaskState.CANCELLED -> AiTaskQueueItemState.CANCELLED
    SummaryQueueTaskState.UNKNOWN -> AiTaskQueueItemState.UNKNOWN
  }

  private companion object {
    const val SUMMARY_PREFIX = "summary:"
    const val LIBRARY_PREFIX = "library-organization:"
  }
}
