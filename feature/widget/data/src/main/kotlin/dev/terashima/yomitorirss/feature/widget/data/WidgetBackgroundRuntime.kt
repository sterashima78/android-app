package dev.terashima.yomitorirss.feature.widget.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.feature.widget.UnreadWidgetRefreshWorker
import dev.terashima.yomitorirss.feature.widget.WidgetRefreshScheduler
import dev.terashima.yomitorirss.feature.widget.WidgetRepository

class WorkManagerWidgetRefreshScheduler(
  context: Context,
) : WidgetRefreshScheduler {
  private val appContext = context.applicationContext

  override fun enqueue() {
    val request = OneTimeWorkRequestBuilder<UnreadWidgetRefreshWorker>()
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  private companion object {
    const val WORK_NAME = "unread-widget-refresh"
  }
}

class WidgetWorkerFactory(
  private val repositoryProvider: () -> WidgetRepository,
  private val onRefreshComplete: (Context) -> Unit,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = if (workerClassName == UnreadWidgetRefreshWorker::class.java.name) {
    UnreadWidgetRefreshWorker(
      appContext = appContext,
      workerParameters = workerParameters,
      repository = repositoryProvider(),
      onRefreshComplete = onRefreshComplete,
    )
  } else {
    null
  }
}
