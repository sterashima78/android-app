package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.background.isBackgroundDataFetchAllowed
import kotlinx.coroutines.CancellationException

class UnreadWidgetRefreshWorker(
  appContext: Context,
  workerParameters: WorkerParameters,
  private val repository: WidgetRepository,
  private val onRefreshComplete: (Context) -> Unit,
) : CoroutineWorker(appContext, workerParameters) {
  override suspend fun doWork(): Result {
    if (!isBackgroundDataFetchAllowed(applicationContext)) return Result.retry()
    return try {
      repository.refreshFeeds(force = inputData.getBoolean("force_refresh", false))
      onRefreshComplete(applicationContext)
      Result.success()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      Result.failure()
    }
  }
}
