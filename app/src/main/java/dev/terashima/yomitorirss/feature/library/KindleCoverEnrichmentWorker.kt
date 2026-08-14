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
import dev.terashima.yomitorirss.feature.library.data.KindleCoverEnrichmentException
import dev.terashima.yomitorirss.feature.library.data.KindleCoverEnrichmentRepository
import dev.terashima.yomitorirss.feature.library.data.LibraryCoverStatusRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal class KindleCoverEnrichmentScheduler(context: Context) {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  val workInfos = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)

  fun sync(enabled: Boolean) {
    if (enabled) schedule() else cancel()
  }

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
    OneTimeWorkRequestBuilder<KindleCoverEnrichmentWorker>()
      .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
      .build()

  companion object {
    private const val WORK_NAME = "kindle-cover-enrichment"
    private const val CONTINUATION_DELAY_MILLIS = 1_100L

    fun continueAfterBatch(context: Context) {
      KindleCoverEnrichmentScheduler(context).scheduleContinuation()
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
    val repository = KindleCoverEnrichmentRepository(connection)
    val coverStatusRepository = LibraryCoverStatusRepository(connection)
    return try {
      if (repository.enrichNext()) {
        KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      }
      Result.success()
    } catch (error: IOException) {
      if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
        Result.retry()
      } else {
        val trace = (error as? KindleCoverEnrichmentException)?.diagnosticTrace
        if (
          coverStatusRepository.markNextKindleCoverLookupError(
            detail = error.message,
            diagnosticTrace = trace,
          )
        ) {
          KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
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
