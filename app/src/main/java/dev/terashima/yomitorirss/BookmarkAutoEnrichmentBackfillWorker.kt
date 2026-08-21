package dev.terashima.yomitorirss

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.summary.data.runBookmarkAutoEnrichmentBackfillWorker

@Deprecated("Compatibility shim for persisted WorkManager requests")
internal class BookmarkAutoEnrichmentBackfillWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = runBookmarkAutoEnrichmentBackfillWorker(applicationContext)
}
