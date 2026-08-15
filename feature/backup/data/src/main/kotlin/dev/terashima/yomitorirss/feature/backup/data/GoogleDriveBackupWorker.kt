package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GoogleDriveBackupWorker(
  appContext: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
  override suspend fun doWork(): Result {
    if (!GoogleDriveBackupPreferences(applicationContext).isConfigured()) return Result.success()

    return runCatching {
      GoogleDriveBackupService(applicationContext).backup()
    }.fold(
      onSuccess = { Result.success() },
      onFailure = { error ->
        when {
          error is SecurityException || error is IllegalArgumentException || error is IllegalStateException ->
            Result.failure()
          runAttemptCount >= MAX_RETRY_COUNT -> Result.failure()
          else -> Result.retry()
        }
      },
    )
  }

  companion object {
    private const val MAX_RETRY_COUNT = 2
  }
}
