package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed

class UnreadWidgetRefreshWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val repository: WidgetRepository,
  private val onRefreshComplete: (Context) -> Unit,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) return Result.retry()
    return try {
      repository.refreshFeeds()
      onRefreshComplete(applicationContext)
      Result.success()
    } catch (_: Throwable) {
      Result.failure()
    }
  }
}
