package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
  private val runBackfill: suspend () -> Unit,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = runBookmarkAutoEnrichmentBackfillWorker(runBackfill)
}

suspend fun runBookmarkAutoEnrichmentBackfillWorker(
  runBackfill: suspend () -> Unit,
): ListenableWorker.Result = withContext(Dispatchers.IO) {
  runCatching { runBackfill() }.fold(
    onSuccess = { ListenableWorker.Result.success() },
    onFailure = { ListenableWorker.Result.retry() },
  )
}
