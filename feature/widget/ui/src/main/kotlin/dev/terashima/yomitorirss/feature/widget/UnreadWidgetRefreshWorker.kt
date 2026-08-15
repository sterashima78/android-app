package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed

class UnreadWidgetRefreshWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) return Result.retry()
    return try {
      applicationContext.requireWidgetRepository().refreshFeeds()
      UnreadArticlesWidgetUpdater.updateAll(applicationContext)
      Result.success()
    } catch (_: Throwable) {
      Result.failure()
    }
  }

  companion object {
    private const val WORK_NAME = "unread-widget-refresh"

    fun enqueue(context: Context) {
      val appContext = context.applicationContext
      val request = OneTimeWorkRequestBuilder<UnreadWidgetRefreshWorker>()
        .setConstraints(backgroundDataFetchConstraints(appContext))
        .build()
      WorkManager.getInstance(appContext).enqueueUniqueWork(
        WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request,
      )
    }
  }
}
