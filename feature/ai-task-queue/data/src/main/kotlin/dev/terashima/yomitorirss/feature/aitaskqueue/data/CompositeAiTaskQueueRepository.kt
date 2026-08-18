package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueExecutionState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

class CompositeAiTaskQueueRepository(
  summaryRepository: SummaryTaskQueueRepository,
  libraryRepository: LibraryOrganizationRepository,
  libraryCatalogRepository: LibraryRepository,
  libraryScheduler: LibraryOrganizationBatchScheduler,
  knowledgeController: KnowledgeBuildTaskController? = null,
) : AiTaskQueueRepository {
  private val summary = SummaryTaskQueueAdapter(summaryRepository)
  private val library = LibraryTaskQueueAdapter(
    repository = libraryRepository,
    catalogRepository = libraryCatalogRepository,
    scheduler = libraryScheduler,
  )
  private val knowledge = knowledgeController?.let(::KnowledgeTaskQueueAdapter)

  override suspend fun listTasks(): List<AiTaskQueueItem> {
    val globalPaused = summary.executionState().paused
    return library.tasks(globalPaused) + summary.tasks() + knowledge.orEmptyTasks()
  }

  override suspend fun executionState(): AiTaskQueueExecutionState =
    summary.executionState().let { state ->
      AiTaskQueueExecutionState(
        paused = state.paused,
        resumeWhenCharging = state.resumeWhenCharging,
      )
    }

  override suspend fun kick() {
    summary.kick()
    library.kickIfRunning()
    knowledge?.kick()
  }

  override suspend fun setPaused(paused: Boolean) {
    val libraryStatus = library.batchStatus()
    if (paused) {
      summary.setPaused(true)
      try {
        library.pauseForGlobalGate(libraryStatus)
        knowledge?.pauseForGlobalGate()
        knowledge?.setResumeOnChargingScheduled(true)
      } catch (error: Throwable) {
        runCatching { summary.setPaused(false) }
        runCatching { library.restoreAfterPauseFailure(libraryStatus) }
        runCatching {
          knowledge?.setResumeOnChargingScheduled(false)
          knowledge?.kick()
        }
        throw error
      }
      return
    }

    summary.setPaused(false)
    try {
      library.resumeFromGlobalGate(libraryStatus)
      knowledge?.setResumeOnChargingScheduled(false)
      knowledge?.kick()
    } catch (error: Throwable) {
      runCatching { summary.setPaused(true) }
      runCatching { library.restorePauseAfterResumeFailure(libraryStatus) }
      runCatching {
        knowledge?.pauseForGlobalGate()
        knowledge?.setResumeOnChargingScheduled(true)
      }
      throw error
    }
  }

  override suspend fun setResumeWhenCharging(enabled: Boolean) {
    val previous = summary.executionState().resumeWhenCharging
    summary.setResumeWhenCharging(enabled)
    try {
      val globalPaused = summary.executionState().paused
      library.setResumeOnChargingScheduled(enabled, globalPaused)
      knowledge?.setResumeOnChargingScheduled(enabled && globalPaused)
    } catch (error: Throwable) {
      runCatching { summary.setResumeWhenCharging(previous) }
      runCatching {
        val globalPaused = summary.executionState().paused
        library.setResumeOnChargingScheduled(previous, globalPaused)
        knowledge?.setResumeOnChargingScheduled(previous && globalPaused)
      }
      throw error
    }
  }

  override suspend fun stop(taskId: String): Boolean =
    summary.stop(taskId) ?: knowledge?.stop(taskId) ?: false

  override suspend fun cancel(taskId: String): Boolean =
    summary.cancel(taskId) ?: knowledge?.cancel(taskId) ?: false

  override suspend fun resume(taskId: String): Boolean {
    summary.resume(taskId)?.let { return it }
    library.resume(taskId, globalPaused = summary.executionState().paused)?.let { return it }
    return knowledge?.resume(taskId) ?: false
  }

  override suspend fun retryFailedBookmarkTasks(): Int = summary.retryFailedBookmarkTasks()

  private suspend fun KnowledgeTaskQueueAdapter?.orEmptyTasks(): List<AiTaskQueueItem> =
    this?.tasks().orEmpty()
}
