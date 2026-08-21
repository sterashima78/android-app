package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.summary.BookmarkAutoEnrichmentBackfillProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BookmarkAutoEnrichmentBackfillScheduler {
  private const val WORK_NAME = "bookmark-auto-enrichment-backfill"

  fun schedule(context: Context) {
    val request = OneTimeWorkRequestBuilder<BookmarkAutoEnrichmentBackfillWorker>().build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }
}

class BookmarkAutoEnrichmentBackfillWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = runBookmarkAutoEnrichmentBackfillWorker(applicationContext)
}

suspend fun runBookmarkAutoEnrichmentBackfillWorker(context: Context): ListenableWorker.Result =
  withContext(Dispatchers.IO) {
    val provider = context.applicationContext as? BookmarkAutoEnrichmentBackfillProvider
      ?: return@withContext ListenableWorker.Result.failure()

    runCatching { provider.runBookmarkAutoEnrichmentBackfill() }.fold(
      onSuccess = { ListenableWorker.Result.success() },
      onFailure = { ListenableWorker.Result.retry() },
    )
  }
