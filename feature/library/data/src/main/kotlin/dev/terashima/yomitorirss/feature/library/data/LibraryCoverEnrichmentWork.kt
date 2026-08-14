package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverEnrichmentCoordinator
import dev.terashima.yomitorirss.feature.library.LibraryCoverWorkSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverWorkState
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class DefaultLibraryCoverEnrichmentCoordinator(
  context: Context,
  database: DatabaseConnection,
) : LibraryCoverEnrichmentCoordinator {
  private val kindleScheduler = KindleCoverEnrichmentScheduler(context)
  private val audibleScheduler = AudibleCoverEnrichmentScheduler(context)
  private val statusRepository = LibraryCoverStatusRepository(database)

  override val workSnapshots: Flow<LibraryCoverWorkSnapshot> = combine(
    kindleScheduler.workInfos,
    audibleScheduler.workInfos,
  ) { kindleWorkInfos, audibleWorkInfos ->
    LibraryCoverWorkSnapshot(
      states = mapOf(
        LibrarySource.KINDLE to coverWorkState(kindleWorkInfos),
        LibrarySource.AUDIBLE to coverWorkState(audibleWorkInfos),
      ),
      finishedWorkCount = (kindleWorkInfos + audibleWorkInfos).count { it.state.isFinished },
    )
  }.distinctUntilChanged()

  override fun sync(kindleEnabled: Boolean) {
    kindleScheduler.sync(kindleEnabled)
    audibleScheduler.schedule()
  }

  override suspend fun snapshot(): LibraryCoverAcquisitionSnapshot = statusRepository.snapshot()

  override suspend fun retryUnresolved(kindleEnabled: Boolean) {
    val sources = buildSet {
      add(LibrarySource.AUDIBLE)
      if (kindleEnabled) add(LibrarySource.KINDLE)
    }
    statusRepository.resetUnresolvedLookups(sources)
    if (kindleEnabled) kindleScheduler.schedule(force = true)
    audibleScheduler.schedule()
  }

  override fun cancel() {
    kindleScheduler.cancel()
    audibleScheduler.cancel()
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

  private fun scheduleContinuation(delayMillis: Long) {
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
    private const val CONTINUATION_DELAY_MILLIS = 1_100L

    fun continueAfterBatch(
      context: Context,
      delayMillis: Long = CONTINUATION_DELAY_MILLIS,
    ) {
      KindleCoverEnrichmentScheduler(context).scheduleContinuation(
        delayMillis.coerceAtLeast(CONTINUATION_DELAY_MILLIS),
      )
    }
  }
}

class KindleCoverEnrichmentWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) {
      KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      return Result.success()
    }

    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val authorizationManager = GoogleBooksAuthorizationManager(applicationContext)
    val repository = KindleCoverEnrichmentRepository(
      database = connection,
      googleBooksAccessTokenProvider = authorizationManager::existingAccessTokenOrNull,
    )
    return try {
      if (repository.enrichNext()) {
        KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      } else {
        repository.nextWakeDelayMillis()?.let { delayMillis ->
          KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext, delayMillis)
        }
      }
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }
}

private class AudibleCoverEnrichmentScheduler(context: Context) {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  val workInfos = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)

  fun schedule() {
    workManager.enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request(),
    )
  }

  private fun scheduleContinuation() {
    workManager.enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.APPEND,
      request(initialDelayMillis = CONTINUATION_DELAY_MILLIS),
    )
  }

  fun cancel() {
    workManager.cancelUniqueWork(WORK_NAME)
  }

  private fun request(initialDelayMillis: Long = 0L) =
    OneTimeWorkRequestBuilder<AudibleCoverEnrichmentWorker>()
      .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()

  companion object {
    private const val WORK_NAME = "audible-cover-enrichment"
    private const val CONTINUATION_DELAY_MILLIS = 1_100L

    fun continueAfterBatch(context: Context) {
      AudibleCoverEnrichmentScheduler(context).scheduleContinuation()
    }
  }
}

class AudibleCoverEnrichmentWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) {
      AudibleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      return Result.success()
    }

    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val repository = AudibleCoverEnrichmentRepository(connection)
    val coverStatusRepository = LibraryCoverStatusRepository(connection)
    return try {
      if (repository.enrichBatch()) {
        AudibleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      }
      Result.success()
    } catch (error: IOException) {
      if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
        Result.retry()
      } else {
        if (coverStatusRepository.markNextAudibleCoverLookupError(error.message)) {
          AudibleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
        }
        Result.success()
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }

  private companion object {
    const val MAX_RETRY_ATTEMPTS = 2
  }
}
