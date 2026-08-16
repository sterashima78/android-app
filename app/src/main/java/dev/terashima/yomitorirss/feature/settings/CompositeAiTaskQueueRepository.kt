package dev.terashima.yomitorirss.feature.settings

import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskSnapshot
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState
import dev.terashima.yomitorirss.feature.knowledge.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidate
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.organizationKey
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

internal class CompositeAiTaskQueueRepository(
  private val summaryRepository: SummaryTaskQueueRepository,
  private val libraryRepository: LibraryOrganizationRepository,
  private val libraryCatalogRepository: LibraryRepository,
  private val libraryScheduler: LibraryOrganizationBatchScheduler,
  private val knowledgeController: WorkManagerKnowledgeBuildTaskController? = null,
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
    val knowledgeTask = knowledgeController?.snapshot()?.let(::toAiTaskQueueItem)
    return libraryTasks + summaryTasks + listOfNotNull(knowledgeTask)
  }

  override suspend fun taskCounts(): AiTaskQueueCounts {
    val summaryCounts = summaryRepository.taskCounts()
    val globalPaused = summaryRepository.executionState().paused
    val batch = libraryRepository.batchSnapshot()
    val libraryStates = batch?.candidates.orEmpty().map { candidate ->
      libraryTaskState(
        candidate = candidate,
        batchStatus = checkNotNull(batch).status,
        globalPaused = globalPaused,
      )
    }
    val knowledgeState = knowledgeController?.snapshot()?.state?.toAiTaskState()
    return AiTaskQueueCounts(
      running = summaryCounts.running +
        libraryStates.count { it == AiTaskQueueItemState.RUNNING } +
        if (knowledgeState == AiTaskQueueItemState.RUNNING) 1 else 0,
      queued = summaryCounts.queued +
        libraryStates.count { it == AiTaskQueueItemState.QUEUED } +
        if (knowledgeState == AiTaskQueueItemState.QUEUED) 1 else 0,
      pausedOrStopped = summaryCounts.stopped +
        libraryStates.count {
          it == AiTaskQueueItemState.PAUSED || it == AiTaskQueueItemState.STOPPED
        } +
        if (
          knowledgeState == AiTaskQueueItemState.PAUSED ||
          knowledgeState == AiTaskQueueItemState.STOPPED
        ) 1 else 0,
    )
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
    knowledgeController?.kick()
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
        knowledgeController?.pauseForGlobalGate()
        knowledgeController?.setResumeOnChargingScheduled(true)
      } catch (error: Throwable) {
        runCatching { summaryRepository.setPaused(false) }
        if (libraryStatus == LibraryOrganizationBatchStatus.RUNNING) {
          runCatching {
            libraryScheduler.setResumeOnChargingScheduled(false)
            libraryScheduler.kick()
          }
        }
        runCatching {
          knowledgeController?.setResumeOnChargingScheduled(false)
          knowledgeController?.kick()
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
      knowledgeController?.setResumeOnChargingScheduled(false)
      knowledgeController?.kick()
    } catch (error: Throwable) {
      runCatching { summaryRepository.setPaused(true) }
      if (libraryStatus == LibraryOrganizationBatchStatus.RUNNING) {
        runCatching {
          libraryScheduler.cancel()
          libraryScheduler.setResumeOnChargingScheduled(true)
        }
      }
      runCatching {
        knowledgeController?.pauseForGlobalGate()
        knowledgeController?.setResumeOnChargingScheduled(true)
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
      knowledgeController?.setResumeOnChargingScheduled(enabled && globalPaused)
    } catch (error: Throwable) {
      runCatching { summaryRepository.setResumeWhenCharging(previous) }
      runCatching {
        val globalPaused = summaryRepository.executionState().paused
        knowledgeController?.setResumeOnChargingScheduled(previous && globalPaused)
      }
      throw error
    }
  }

  override suspend fun stop(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.stop(taskId.removePrefix(SUMMARY_PREFIX))
    taskId == KNOWLEDGE_TASK_ID -> knowledgeController?.stop() ?: false
    else -> false
  }

  override suspend fun cancel(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.cancel(taskId.removePrefix(SUMMARY_PREFIX))
    taskId == KNOWLEDGE_TASK_ID -> knowledgeController?.cancel() ?: false
    else -> false
  }

  override suspend fun resume(taskId: String): Boolean = when {
    taskId.startsWith(SUMMARY_PREFIX) -> summaryRepository.resume(taskId.removePrefix(SUMMARY_PREFIX))
    taskId.startsWith(LIBRARY_PREFIX) -> retryLibraryTask(taskId)
    taskId == KNOWLEDGE_TASK_ID -> knowledgeController?.resume() ?: false
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
    progressStage = task.progressStage?.toAiTaskProgressStage(),
    progressCurrent = task.progressCurrent,
    progressTotal = task.progressTotal,
    error = task.error,
    canStop = task.state == SummaryQueueTaskState.QUEUED || task.state == SummaryQueueTaskState.RUNNING,
    canCancel = task.state == SummaryQueueTaskState.QUEUED ||
      task.state == SummaryQueueTaskState.RUNNING ||
      task.state == SummaryQueueTaskState.STOPPED,
    canResume = task.state == SummaryQueueTaskState.STOPPED || task.state == SummaryQueueTaskState.FAILED,
  )

  private fun toAiTaskQueueItem(task: KnowledgeBuildTaskSnapshot): AiTaskQueueItem {
    val state = task.state.toAiTaskState()
    return AiTaskQueueItem(
      id = KNOWLEDGE_TASK_ID,
      kind = AiTaskQueueItemKind.KNOWLEDGE_WIKI,
      title = "自動Wikiを構築",
      source = "保存済み要約",
      state = state,
      error = task.error,
      canStop = state == AiTaskQueueItemState.QUEUED || state == AiTaskQueueItemState.RUNNING,
      canCancel = state == AiTaskQueueItemState.QUEUED ||
        state == AiTaskQueueItemState.RUNNING ||
        state == AiTaskQueueItemState.PAUSED ||
        state == AiTaskQueueItemState.STOPPED ||
        state == AiTaskQueueItemState.FAILED,
      canResume = state == AiTaskQueueItemState.STOPPED || state == AiTaskQueueItemState.FAILED,
    )
  }

  private fun toAiTaskQueueItem(
    candidate: LibraryOrganizationCandidate,
    batchStatus: LibraryOrganizationBatchStatus,
    globalPaused: Boolean,
    book: LibraryBook?,
  ): AiTaskQueueItem {
    val state = libraryTaskState(
      candidate = candidate,
      batchStatus = batchStatus,
      globalPaused = globalPaused,
    )
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

  private fun libraryTaskState(
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

  private fun KnowledgeBuildTaskState.toAiTaskState(): AiTaskQueueItemState = when (this) {
    KnowledgeBuildTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
    KnowledgeBuildTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
    KnowledgeBuildTaskState.PAUSED -> AiTaskQueueItemState.PAUSED
    KnowledgeBuildTaskState.STOPPED -> AiTaskQueueItemState.STOPPED
    KnowledgeBuildTaskState.FAILED -> AiTaskQueueItemState.FAILED
  }

  private fun SummaryQueueTaskProgressStage.toAiTaskProgressStage(): AiTaskQueueProgressStage = when (this) {
    SummaryQueueTaskProgressStage.FETCHING_ARTICLE -> AiTaskQueueProgressStage.FETCHING_CONTENT
    SummaryQueueTaskProgressStage.PREPARING_MODEL -> AiTaskQueueProgressStage.PREPARING_MODEL
    SummaryQueueTaskProgressStage.GENERATING_SUMMARY -> AiTaskQueueProgressStage.GENERATING
    SummaryQueueTaskProgressStage.SUMMARIZING_CHUNK -> AiTaskQueueProgressStage.PROCESSING_CHUNK
    SummaryQueueTaskProgressStage.REDUCING_SUMMARY -> AiTaskQueueProgressStage.REDUCING
    SummaryQueueTaskProgressStage.FINALIZING_SUMMARY -> AiTaskQueueProgressStage.FINALIZING
    SummaryQueueTaskProgressStage.UNKNOWN -> AiTaskQueueProgressStage.UNKNOWN
  }

  private companion object {
    const val SUMMARY_PREFIX = "summary:"
    const val LIBRARY_PREFIX = "library-organization:"
    const val KNOWLEDGE_TASK_ID = "knowledge:auto-wiki"
  }
}
