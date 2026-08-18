package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemPriority
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidate
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.organizationKey

internal class LibraryTaskQueueAdapter(
  private val repository: LibraryOrganizationRepository,
  private val catalogRepository: LibraryRepository,
  private val scheduler: LibraryOrganizationBatchScheduler,
) {
  suspend fun tasks(globalPaused: Boolean): List<AiTaskQueueItem> {
    val batch = repository.batchSnapshot() ?: return emptyList()
    val booksByKey = catalogRepository.snapshot().let { snapshot ->
      (snapshot.books + snapshot.hiddenBooks).associateBy(LibraryBook::organizationKey)
    }
    return batch.candidates.map { candidate ->
      toAiTaskQueueItem(
        candidate = candidate,
        batchStatus = batch.status,
        globalPaused = globalPaused,
        book = booksByKey[candidate.key],
      )
    }
  }

  suspend fun batchStatus(): LibraryOrganizationBatchStatus? = repository.batchSnapshot()?.status

  fun kick() = scheduler.kick()

  suspend fun kickIfRunning() {
    if (batchStatus() == LibraryOrganizationBatchStatus.RUNNING) scheduler.kick()
  }

  suspend fun pauseForGlobalGate(status: LibraryOrganizationBatchStatus?) {
    when (status) {
      LibraryOrganizationBatchStatus.RUNNING -> {
        scheduler.cancel()
        scheduler.setResumeOnChargingScheduled(true)
      }
      LibraryOrganizationBatchStatus.PAUSED -> {
        scheduler.setResumeOnChargingScheduled(false)
      }
      LibraryOrganizationBatchStatus.COMPLETED,
      null -> scheduler.setResumeOnChargingScheduled(false)
    }
  }

  suspend fun resumeFromGlobalGate(status: LibraryOrganizationBatchStatus?) {
    scheduler.setResumeOnChargingScheduled(false)
    if (status == LibraryOrganizationBatchStatus.RUNNING) scheduler.kick()
  }

  suspend fun restoreAfterPauseFailure(status: LibraryOrganizationBatchStatus?) {
    scheduler.setResumeOnChargingScheduled(false)
    if (status == LibraryOrganizationBatchStatus.RUNNING) scheduler.kick()
  }

  suspend fun restorePauseAfterResumeFailure(status: LibraryOrganizationBatchStatus?) {
    if (status == LibraryOrganizationBatchStatus.RUNNING) {
      scheduler.cancel()
      scheduler.setResumeOnChargingScheduled(true)
    }
  }

  suspend fun setResumeOnChargingScheduled(enabled: Boolean, globalPaused: Boolean) {
    val running = batchStatus() == LibraryOrganizationBatchStatus.RUNNING
    scheduler.setResumeOnChargingScheduled(enabled && globalPaused && running)
  }

  suspend fun resume(taskId: String, globalPaused: Boolean): Boolean? {
    if (!taskId.startsWith(PREFIX)) return null
    if (globalPaused) return false
    val batch = repository.batchSnapshot() ?: return false
    if (batch.status == LibraryOrganizationBatchStatus.PAUSED) return false
    val candidate = batch.candidates.firstOrNull { taskId == taskId(it) } ?: return false
    if (
      candidate.status != LibraryOrganizationCandidateStatus.FAILED &&
      candidate.status != LibraryOrganizationCandidateStatus.SKIPPED
    ) {
      return false
    }

    repository.retryCandidate(candidate.key)
    scheduler.kick()
    return true
  }

  private fun toAiTaskQueueItem(
    candidate: LibraryOrganizationCandidate,
    batchStatus: LibraryOrganizationBatchStatus,
    globalPaused: Boolean,
    book: LibraryBook?,
  ): AiTaskQueueItem {
    val state = taskState(candidate, batchStatus, globalPaused)
    return AiTaskQueueItem(
      id = taskId(candidate),
      kind = AiTaskQueueItemKind.LIBRARY_ORGANIZATION,
      title = book?.title ?: candidate.key.sourceId,
      source = candidate.key.source.label,
      state = state,
      priority = AiTaskQueueItemPriority.NORMAL,
      error = candidate.error,
      canResume = !globalPaused &&
        batchStatus != LibraryOrganizationBatchStatus.PAUSED &&
        (candidate.status == LibraryOrganizationCandidateStatus.FAILED ||
          candidate.status == LibraryOrganizationCandidateStatus.SKIPPED),
    )
  }

  private fun taskState(
    candidate: LibraryOrganizationCandidate,
    batchStatus: LibraryOrganizationBatchStatus,
    globalPaused: Boolean,
  ): AiTaskQueueItemState {
    val waitingForAi = candidate.status == LibraryOrganizationCandidateStatus.QUEUED ||
      candidate.status == LibraryOrganizationCandidateStatus.PROCESSING
    val effectivelyPaused = waitingForAi && (
      globalPaused || batchStatus == LibraryOrganizationBatchStatus.PAUSED
    )
    return when {
      effectivelyPaused -> AiTaskQueueItemState.PAUSED
      candidate.status == LibraryOrganizationCandidateStatus.QUEUED -> AiTaskQueueItemState.QUEUED
      candidate.status == LibraryOrganizationCandidateStatus.PROCESSING -> AiTaskQueueItemState.RUNNING
      candidate.status == LibraryOrganizationCandidateStatus.FAILED ||
        candidate.status == LibraryOrganizationCandidateStatus.SKIPPED -> AiTaskQueueItemState.FAILED
      else -> AiTaskQueueItemState.COMPLETED
    }
  }

  private fun taskId(candidate: LibraryOrganizationCandidate): String =
    "$PREFIX${candidate.batchId}:${candidate.key.source.name}:${candidate.key.sourceId}"

  private companion object {
    const val PREFIX = "library-organization:"
  }
}
