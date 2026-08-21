package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemPriority
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationItem
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus

internal class SmbMetadataNormalizationTaskQueueAdapter(
  private val repository: SmbMetadataNormalizationRepository,
  private val scheduler: SmbMetadataNormalizationScheduler,
) {
  suspend fun tasks(globalPaused: Boolean): List<AiTaskQueueItem> {
    val batch = repository.batchSnapshot() ?: return emptyList()
    return batch.items.map { item -> toTask(item, globalPaused) }
  }

  suspend fun kickIfRunning() {
    if (repository.batchSnapshot()?.status == SmbMetadataNormalizationBatchStatus.RUNNING) scheduler.kick()
  }

  suspend fun pauseForGlobalGate() {
    val batch = repository.batchSnapshot() ?: return
    if (batch.status == SmbMetadataNormalizationBatchStatus.RUNNING && batch.hasActiveWork) {
      scheduler.cancel()
      scheduler.setResumeOnChargingScheduled(true)
    } else {
      scheduler.setResumeOnChargingScheduled(false)
    }
  }

  suspend fun resumeFromGlobalGate() {
    scheduler.setResumeOnChargingScheduled(false)
    kickIfRunning()
  }

  suspend fun setResumeOnChargingScheduled(enabled: Boolean, globalPaused: Boolean) {
    val batch = repository.batchSnapshot()
    val shouldSchedule = enabled && globalPaused &&
      batch?.status == SmbMetadataNormalizationBatchStatus.RUNNING &&
      batch.hasActiveWork
    scheduler.setResumeOnChargingScheduled(shouldSchedule)
  }

  suspend fun resume(taskId: String, globalPaused: Boolean): Boolean? {
    if (!taskId.startsWith(PREFIX)) return null
    if (globalPaused) return false
    val batch = repository.batchSnapshot() ?: return false
    val item = batch.items.firstOrNull { taskId == taskId(it) } ?: return false
    if (item.status != SmbMetadataNormalizationStatus.FAILED &&
      item.status != SmbMetadataNormalizationStatus.SKIPPED
    ) {
      return false
    }
    repository.retryCandidate(item.sourceId)
    scheduler.kick()
    return true
  }

  private fun toTask(
    item: SmbMetadataNormalizationItem,
    globalPaused: Boolean,
  ): AiTaskQueueItem {
    val waiting = item.status == SmbMetadataNormalizationStatus.WAITING_FOR_COVER ||
      item.status == SmbMetadataNormalizationStatus.QUEUED ||
      item.status == SmbMetadataNormalizationStatus.PROCESSING
    val state = when {
      globalPaused && waiting -> AiTaskQueueItemState.PAUSED
      item.status == SmbMetadataNormalizationStatus.WAITING_FOR_COVER -> AiTaskQueueItemState.QUEUED
      item.status == SmbMetadataNormalizationStatus.QUEUED -> AiTaskQueueItemState.QUEUED
      item.status == SmbMetadataNormalizationStatus.PROCESSING -> AiTaskQueueItemState.RUNNING
      item.status == SmbMetadataNormalizationStatus.FAILED ||
        item.status == SmbMetadataNormalizationStatus.SKIPPED -> AiTaskQueueItemState.FAILED
      else -> AiTaskQueueItemState.COMPLETED
    }
    return AiTaskQueueItem(
      id = taskId(item),
      kind = AiTaskQueueItemKind.SMB_METADATA_NORMALIZATION,
      title = item.originalFileName,
      source = "ファイルサーバ",
      state = state,
      priority = AiTaskQueueItemPriority.LOW,
      pendingReviewCount = 1.takeIf { item.status == SmbMetadataNormalizationStatus.PENDING_REVIEW },
      error = item.error,
      canResume = !globalPaused &&
        (item.status == SmbMetadataNormalizationStatus.FAILED ||
          item.status == SmbMetadataNormalizationStatus.SKIPPED),
    )
  }

  private fun taskId(item: SmbMetadataNormalizationItem): String =
    "$PREFIX${item.batchId}:${item.sourceId}"

  private companion object {
    const val PREFIX = "smb-metadata-normalization:"
  }
}
