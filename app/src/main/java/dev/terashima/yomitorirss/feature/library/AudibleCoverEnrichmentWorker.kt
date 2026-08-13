package dev.terashima.yomitorirss.feature.library

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.data.AudibleCoverEnrichmentRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal class AudibleCoverEnrichmentScheduler(context: Context) {
  private val workManager = WorkManager.getInstance(context.applicationContext)
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

  private fun request(initialDelayMillis: Long = 0L) =
    OneTimeWorkRequestBuilder<AudibleCoverEnrichmentWorker>()
      .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build(),
      )
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
    val database = YomitoriDatabase.create(applicationContext)
    val repository = AudibleCoverEnrichmentRepository(DatabaseConnection(database))
    return try {
      if (repository.enrichBatch()) {
        AudibleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      }
      Result.success()
    } catch (_: IOException) {
      Result.retry()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }
}
