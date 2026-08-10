package dev.terashima.yomitorirss.feature.widget
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class UnreadWidgetRefreshWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result = try {
    applicationContext.requireWidgetRepository().refreshFeeds()
    UnreadArticlesWidgetUpdater.updateAll(applicationContext)
    Result.success()
  } catch (_: Throwable) {
    Result.failure()
  }

  companion object {
    private const val WORK_NAME = "unread-widget-refresh"

    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<UnreadWidgetRefreshWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        )
        .build()
      WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request,
      )
    }
  }
}
