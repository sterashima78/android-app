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
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryRepository
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class KindleCoverEnrichmentScheduler(context: Context) {
  private val workManager = WorkManager.getInstance(context.applicationContext)

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
      request(),
    )
  }

  fun cancel() {
    workManager.cancelUniqueWork(WORK_NAME)
  }

  private fun request() = OneTimeWorkRequestBuilder<KindleCoverEnrichmentWorker>()
    .setConstraints(
      Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build(),
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

  companion object {
    private const val WORK_NAME = "kindle-cover-enrichment"

    fun continueAfterBatch(context: Context) {
      KindleCoverEnrichmentScheduler(context).scheduleContinuation()
    }
  }
}

internal class KindleCoverEnrichmentWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
  override suspend fun doWork(): Result {
    val database = YomitoriDatabase.create(applicationContext)
    val repository = DefaultLibraryRepository(DatabaseConnection(database))
    return try {
      if (repository.enrichKindleCoverBatch()) {
        KindleCoverEnrichmentScheduler.continueAfterBatch(applicationContext)
      }
      Result.success()
    } catch (_: IOException) {
      Result.retry()
    } catch (_: Throwable) {
      Result.failure()
    } finally {
      database.close()
    }
  }
}
