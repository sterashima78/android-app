package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueExecutionState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskReader
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskScheduler
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

class CompositeAiTaskQueueRepository(
  summaryRepository: SummaryTaskQueueRepository,
  libraryRepository: LibraryOrganizationRepository,
  libraryCatalogRepository: LibraryRepository,
  libraryScheduler: LibraryOrganizationBatchScheduler,
  knowledgeController: KnowledgeBuildTaskController? = null,
  knowledgeExecutionSettings: KnowledgeExecutionSettings? = null,
  smbMetadataNormalizationRepository: SmbMetadataNormalizationRepository? = null,
  smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler? = null,
  podcastTaskReader: PodcastGenerationTaskReader? = null,
  rssRecommendationTaskReader: RssRecommendationTaskReader? = null,
  rssRecommendationTaskScheduler: RssRecommendationTaskScheduler? = null,
) : AiTaskQueueRepository {
  private val summary = SummaryTaskQueueAdapter(summaryRepository)
  private val library = LibraryTaskQueueAdapter(
    repository = libraryRepository,
    catalogRepository = libraryCatalogRepository,
    scheduler = libraryScheduler,
  )
  private val smbMetadata = if (
    smbMetadataNormalizationRepository != null && smbMetadataNormalizationScheduler != null
  ) {
    SmbMetadataNormalizationTaskQueueAdapter(
      repository = smbMetadataNormalizationRepository,
      scheduler = smbMetadataNormalizationScheduler,
    )
  } else {
    null
  }
  private val knowledge = if (knowledgeController != null && knowledgeExecutionSettings != null) {
    KnowledgeTaskQueueAdapter(knowledgeController, knowledgeExecutionSettings)
  } else {
    null
  }
  private val podcast = podcastTaskReader?.let(::PodcastTaskQueueAdapter)
  private val rssRecommendation = if (
    rssRecommendationTaskReader != null && rssRecommendationTaskScheduler != null
  ) {
    RssRecommendationTaskQueueAdapter(
      reader = rssRecommendationTaskReader,
      scheduler = rssRecommendationTaskScheduler,
    )
  } else {
    null
  }

  override suspend fun listTasks(): List<AiTaskQueueItem> {
    val executionState = summary.executionState()
    val localPaused = executionState.localPaused
    val cloudPaused = executionState.cloudPaused
    return library.tasks(localPaused) +
      smbMetadata.orEmptyTasks(localPaused) +
      rssRecommendation.orEmptyTasks(localPaused, cloudPaused) +
      summary.tasks() +
      knowledge.orEmptyTasks() +
      podcast.orEmptyTasks()
  }

  override suspend fun executionState(): AiTaskQueueExecutionState =
    summary.executionState().let { state ->
      AiTaskQueueExecutionState(
        localPaused = state.localPaused,
        cloudPaused = state.cloudPaused,
        resumeLocalWhenCharging = state.resumeLocalWhenCharging,
      )
    }

  override suspend fun kick() {
    summary.kick()
    rssRecommendation?.kick()
    library.kickIfRunning()
    smbMetadata?.kickIfRunning()
    knowledge?.kick()
  }

  override suspend fun setLocalPaused(paused: Boolean) {
    val libraryStatus = library.batchStatus()
    val pauseKnowledge = knowledge?.usesLocalProvider() == true
    val pauseRss = rssRecommendation?.usesLocalProvider() == true
    if (paused) {
      summary.setLocalPaused(true)
      try {
        if (pauseRss) rssRecommendation?.pauseForGlobalGate(resumeOnCharging = true)
        library.pauseForGlobalGate(libraryStatus)
        smbMetadata?.pauseForGlobalGate()
        if (pauseKnowledge) {
          knowledge?.pauseForGlobalGate()
          knowledge?.setResumeOnChargingScheduled(true)
        }
      } catch (error: Throwable) {
        runCatching { summary.setLocalPaused(false) }
        if (pauseRss) runCatching { rssRecommendation?.resumeFromGlobalGate() }
        runCatching { library.restoreAfterPauseFailure(libraryStatus) }
        runCatching { smbMetadata?.resumeFromGlobalGate() }
        if (pauseKnowledge) {
          runCatching {
            knowledge?.setResumeOnChargingScheduled(false)
            knowledge?.kick()
          }
        }
        throw error
      }
      return
    }

    summary.setLocalPaused(false)
    try {
      if (pauseRss) rssRecommendation?.resumeFromGlobalGate()
      library.resumeFromGlobalGate(libraryStatus)
      smbMetadata?.resumeFromGlobalGate()
      if (pauseKnowledge) {
        knowledge?.setResumeOnChargingScheduled(false)
        knowledge?.kick()
      }
    } catch (error: Throwable) {
      runCatching { summary.setLocalPaused(true) }
      if (pauseRss) runCatching { rssRecommendation?.pauseForGlobalGate(resumeOnCharging = true) }
      runCatching { library.restorePauseAfterResumeFailure(libraryStatus) }
      runCatching { smbMetadata?.pauseForGlobalGate() }
      if (pauseKnowledge) {
        runCatching {
          knowledge?.pauseForGlobalGate()
          knowledge?.setResumeOnChargingScheduled(true)
        }
      }
      throw error
    }
  }

  override suspend fun setCloudPaused(paused: Boolean) {
    val pauseKnowledge = knowledge?.usesCloudProvider() == true
    val pauseRss = rssRecommendation?.usesCloudProvider() == true
    if (paused) {
      summary.setCloudPaused(true)
      try {
        if (pauseKnowledge) knowledge?.pauseForGlobalGate()
        if (pauseRss) rssRecommendation?.pauseForGlobalGate(resumeOnCharging = false)
      } catch (error: Throwable) {
        runCatching { summary.setCloudPaused(false) }
        if (pauseKnowledge) runCatching { knowledge?.kick() }
        if (pauseRss) runCatching { rssRecommendation?.resumeFromGlobalGate() }
        throw error
      }
      return
    }

    summary.setCloudPaused(false)
    try {
      if (pauseKnowledge) knowledge?.kick()
      if (pauseRss) rssRecommendation?.resumeFromGlobalGate()
    } catch (error: Throwable) {
      runCatching { summary.setCloudPaused(true) }
      if (pauseKnowledge) runCatching { knowledge?.pauseForGlobalGate() }
      if (pauseRss) runCatching { rssRecommendation?.pauseForGlobalGate(resumeOnCharging = false) }
      throw error
    }
  }

  override suspend fun setResumeLocalWhenCharging(enabled: Boolean) {
    val previous = summary.executionState().resumeLocalWhenCharging
    summary.setResumeLocalWhenCharging(enabled)
    try {
      val localPaused = summary.executionState().localPaused
      library.setResumeOnChargingScheduled(enabled, localPaused)
      rssRecommendation?.setResumeOnChargingScheduled(enabled, localPaused)
      smbMetadata?.setResumeOnChargingScheduled(enabled, localPaused)
      if (knowledge?.usesLocalProvider() == true) {
        knowledge.setResumeOnChargingScheduled(enabled && localPaused)
      }
    } catch (error: Throwable) {
      runCatching { summary.setResumeLocalWhenCharging(previous) }
      runCatching {
        val localPaused = summary.executionState().localPaused
        library.setResumeOnChargingScheduled(previous, localPaused)
        rssRecommendation?.setResumeOnChargingScheduled(previous, localPaused)
        smbMetadata?.setResumeOnChargingScheduled(previous, localPaused)
        if (knowledge?.usesLocalProvider() == true) {
          knowledge.setResumeOnChargingScheduled(previous && localPaused)
        }
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
    val localPaused = summary.executionState().localPaused
    library.resume(taskId, globalPaused = localPaused)?.let { return it }
    smbMetadata?.resume(taskId, globalPaused = localPaused)?.let { return it }
    return knowledge?.resume(taskId) ?: false
  }

  override suspend fun retryFailedBookmarkTasks(): Int = summary.retryFailedBookmarkTasks()

  private fun RssRecommendationTaskQueueAdapter?.orEmptyTasks(
    localPaused: Boolean,
    cloudPaused: Boolean,
  ): List<AiTaskQueueItem> = this?.tasks(localPaused, cloudPaused).orEmpty()

  private suspend fun KnowledgeTaskQueueAdapter?.orEmptyTasks(): List<AiTaskQueueItem> =
    this?.tasks().orEmpty()

  private suspend fun SmbMetadataNormalizationTaskQueueAdapter?.orEmptyTasks(
    globalPaused: Boolean,
  ): List<AiTaskQueueItem> = this?.tasks(globalPaused).orEmpty()

  private suspend fun PodcastTaskQueueAdapter?.orEmptyTasks(): List<AiTaskQueueItem> =
    this?.tasks().orEmpty()
}
