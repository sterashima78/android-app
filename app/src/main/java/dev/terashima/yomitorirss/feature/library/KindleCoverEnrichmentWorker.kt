package dev.terashima.yomitorirss.feature.library

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.KindleCoverEnrichmentRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal class KindleCoverEnrichmentScheduler(context: Context) {
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
