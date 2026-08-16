package dev.terashima.yomitorirss.feature.settings

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidate
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.organizationKey
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

internal class CompositeAiTaskQueueRepository(
  private val summaryRepository: SummaryTaskQueueRepository,
  private val libraryRepository: LibraryOrganizationRepository,
  private val libraryCatalogRepository: LibraryRepository,
  private val libraryScheduler: LibraryOrganizationBatchScheduler,
) : AiTaskQueueRepository {
  override suspend fun listTasks(): List<AiTaskQueueItem> {
    val globalPaused = summaryRepository.executionState().paused
    val batch = libraryRepository.batchSnapshot()
    val booksByKey = if (batch == null) {
      emptyMap()
    } else {
      libraryCatalogRepository.snapshot().let { snapshot ->
        (snapshot.books + snapshot.hiddenBooks).associateBy(LibraryBook::organizationKey)
      }
    }
    val libraryTasks = batch?.candidates.orEmpty().map { candidate ->
      toAiTaskQueueItem(
        candidate = candidate,
        batchStatus = checkNotNull(batch).status,
        globalPaused = globalPaused,
        book = booksByKey[candidate.key],
      )
    }
    val summaryTasks = summaryRepository.listTasks().map(::toAiTaskQueueItem)
    return libraryTasks + summaryTasks
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
    val libraryStatus = libraryRepository.batchSnapshot()?.status
    if (paused) {
      summaryRepository.setPaused(true)
      try {
        when (libraryStatus) {
          LibraryOrganizationBatchStatus.RUNNING -> {
            libraryScheduler.cancel()
            libraryScheduler.setResumeOnChargingScheduled(true)
          }
          LibraryOrganizationBatchStatus.PAUSED -> {
            // An explicitly paused library batch must stay paused when the global gate later opens.
            libraryScheduler.setResumeOnChargingScheduled(false)
          }
          LibraryOrganizationBatchStatus.COMPLETED,
          null -> libraryScheduler.setResumeOnChargingScheduled(false)
        }
      } catch (error: Throwable) {
        runCatching { summaryRepository.setPaused(false) }
        if (libraryStatus == LibraryOrganizationBatchStatus.RUNNING) {
          runCatching {
            libraryScheduler.setResumeOnChargingScheduled(false)
            libraryScheduler.kick()
          }
        }
        throw error
      }
      return
    }

    summaryRepository.setPaused(false)
    try {
      libraryScheduler.setResumeOnChargingScheduled(false)
      if (libraryStatus == LibraryOrganizationBatchStatus.RUNNING) {
        libraryScheduler.kick()
      }
    } catch (error: Throwable) {
      runCatching { summaryRepository.setPaused(true) }
      if (libraryStatus == LibraryOrganizationBatchStatus.RUNNING) {
        runCatching {
          libraryScheduler.cancel()
          libraryScheduler.setResumeOnChargingScheduled(true)
        }
      }
      throw error
    }
  }

  override suspend fun setResumeWhenCharging(enabled: Boolean) {
    val previous = summaryRepository.executionState().resumeWhenCharging
    summaryRepository.setResumeWhenCharging(enabled)
    try {
      val globalPaused = summaryRepository.executionState().paused
      val libraryRunning = libraryRepository.batchSnapshot()?.status == LibraryOrganizationBatchStatus.RUNNING
      libraryScheduler.setResumeOnChargingScheduled(
        enabled = enabled && globalPaused && libraryRunning,
      )
    } catch (error: Throwable) {
      runCatching { summaryRepository.setResumeWhenCharging(previous) }
      throw error
    }
  }

  override suspend fun stop(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.stop(taskId.removePrefix(SUMMARY_PREFIX))
    else -> false
  }

  override suspend fun cancel(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.cancel(taskId.removePrefix(SUMMARY_PREFIX))
    else -> false
  }

  override suspend fun resume(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.resume(taskId.removePrefix(SUMMARY_PREFIX))
    taskId.startsWith(LIBRARY_PREFIX) -> retryLibraryTask(taskId)
    else -> false
  }

  private suspend fun retryLibraryTask(taskId: String): Boolean {
    if (summaryRepository.executionState().paused) return false
    val batch = libraryRepository.batchSnapshot() ?: return false
    if (batch.status == LibraryOrganizationBatchStatus.PAUSED) return false
    val candidate = batch.candidates.firstOrNull { candidate ->
      taskId == libraryTaskId(candidate)
    } ?: return false
    if (
      candidate.status != LibraryOrganizationCandidateStatus.FAILED &&
      candidate.status != LibraryOrganizationCandidateStatus.SKIPPED
    ) {
      return false
    }

    libraryRepository.retryCandidate(candidate.key)
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
    candidate: LibraryOrganizationCandidate,
    batchStatus: LibraryOrganizationBatchStatus,
    globalPaused: Boolean,
    book: LibraryBook?,
  ): AiTaskQueueItem {
    val waitingForAi = candidate.status == LibraryOrganizationCandidateStatus.QUEUED ||
      candidate.status == LibraryOrganizationCandidateStatus.PROCESSING
    val effectivelyPaused = waitingForAi && (
      globalPaused || batchStatus == LibraryOrganizationBatchStatus.PAUSED
    )
    val state = when {
      effectivelyPaused -> AiTaskQueueItemState.PAUSED
      candidate.status == LibraryOrganizationCandidateStatus.QUEUED -> AiTaskQueueItemState.QUEUED
      candidate.status == LibraryOrganizationCandidateStatus.PROCESSING -> AiTaskQueueItemState.RUNNING
      candidate.status == LibraryOrganizationCandidateStatus.FAILED ||
        candidate.status == LibraryOrganizationCandidateStatus.SKIPPED -> AiTaskQueueItemState.FAILED
      else -> AiTaskQueueItemState.COMPLETED
    }
    return AiTaskQueueItem(
      id = libraryTaskId(candidate),
      kind = AiTaskQueueItemKind.LIBRARY_ORGANIZATION,
      title = book?.title ?: candidate.key.sourceId,
      source = candidate.key.source.label,
      state = state,
      error = candidate.error,
      canResume = !globalPaused &&
        batchStatus != LibraryOrganizationBatchStatus.PAUSED &&
        (candidate.status == LibraryOrganizationCandidateStatus.FAILED ||
          candidate.status == LibraryOrganizationCandidateStatus.SKIPPED),
    )
  }

  private fun libraryTaskId(candidate: LibraryOrganizationCandidate): String =
    "$LIBRARY_PREFIX${candidate.batchId}:${candidate.key.source.name}:${candidate.key.sourceId}"

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
