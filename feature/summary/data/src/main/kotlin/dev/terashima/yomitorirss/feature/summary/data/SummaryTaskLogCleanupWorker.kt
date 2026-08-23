package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SummaryTaskLogCleanupWorker(
  appContext: Context,
  params: WorkerParameters,
  private val database: YomitoriDatabase,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      val cutoff = Instant.now()
        .minus(SUMMARY_TASK_LOG_RETENTION_DAYS, ChronoUnit.DAYS)
        .toString()
      database.deleteFinishedSummaryTasksBefore(cutoff)
      Result.success()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      Result.retry()
    }
  }
}

internal const val SUMMARY_TASK_LOG_RETENTION_DAYS = 30L
