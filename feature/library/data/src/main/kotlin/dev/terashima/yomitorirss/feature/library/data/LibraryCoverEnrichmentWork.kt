package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.KindleCoverEnrichmentWorker
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverEnrichmentCoordinator
import dev.terashima.yomitorirss.feature.library.LibraryCoverWorkSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverWorkState
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DefaultLibraryCoverEnrichmentCoordinator(
  context: Context,
  database: DatabaseConnection,
) : LibraryCoverEnrichmentCoordinator {
  private val kindleScheduler = KindleCoverEnrichmentScheduler(context)
  private val statusRepository = LibraryCoverStatusRepository(database)

  override val workSnapshots: Flow<LibraryCoverWorkSnapshot> = kindleScheduler.workInfos
    .map { kindleWorkInfos ->
      LibraryCoverWorkSnapshot(
        states = mapOf(
          LibrarySource.KINDLE to coverWorkState(kindleWorkInfos),
        ),
        finishedWorkCount = kindleWorkInfos.count { it.state.isFinished },
      )
    }
    .distinctUntilChanged()

  override fun sync(kindleEnabled: Boolean) {
    kindleScheduler.sync(kindleEnabled)
  }

  override suspend fun snapshot(): LibraryCoverAcquisitionSnapshot =
    statusRepository.snapshot().let { snapshot ->
      snapshot.copy(items = snapshot.items.filter { it.source == LibrarySource.KINDLE })
    }

  override suspend fun retryUnresolved(kindleEnabled: Boolean) {
    if (!kindleEnabled) return
    statusRepository.resetUnresolvedLookups(setOf(LibrarySource.KINDLE))
    kindleScheduler.schedule(force = true)
  }

  override fun cancel() {
    kindleScheduler.cancel()
  }
}

private fun coverWorkState(workInfos: List<WorkInfo>): LibraryCoverWorkState {
  val active = workInfos.filterNot { it.state.isFinished }
  if (active.any { it.state == WorkInfo.State.RUNNING }) return LibraryCoverWorkState.RUNNING
  if (active.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }) {
    return LibraryCoverWorkState.RETRY_WAITING
  }
  if (active.isNotEmpty()) return LibraryCoverWorkState.WAITING
  return if (workInfos.lastOrNull()?.state == WorkInfo.State.FAILED) {
    LibraryCoverWorkState.FAILED
  } else {
    LibraryCoverWorkState.IDLE
  }
}

private class KindleCoverEnrichmentScheduler(context: Context) {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  val workInfos = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)

  fun sync(enabled: Boolean) {
    if (enabled) schedule() else cancel()
  }

  fun schedule(force: Boolean = false) {
    workManager.enqueueUniqueWork(
      WORK_NAME,
      if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
      request(),
    )
  }

  fun scheduleContinuation(delayMillis: Long) {
    workManager.enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.APPEND,
      request(initialDelayMillis = delayMillis),
    )
  }

  fun cancel() {
    workManager.cancelUniqueWork(WORK_NAME)
  }

  private fun request(initialDelayMillis: Long = 0L) =
    OneTimeWorkRequestBuilder<KindleCoverEnrichmentWorker>()
      .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
      .build()

  companion object {
    private const val WORK_NAME = "kindle-cover-enrichment"
  }
}

internal fun continueKindleCoverEnrichment(
  context: Context,
  delayMillis: Long = CONTINUATION_DELAY_MILLIS,
) {
  KindleCoverEnrichmentScheduler(context).scheduleContinuation(
    delayMillis.coerceAtLeast(CONTINUATION_DELAY_MILLIS),
  )
}

private const val CONTINUATION_DELAY_MILLIS = 1_100L
