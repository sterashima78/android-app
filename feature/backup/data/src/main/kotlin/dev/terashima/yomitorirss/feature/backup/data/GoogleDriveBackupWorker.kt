package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.backup.BackupRepository

class GoogleDriveBackupWorker(
  appContext: Context,
  parameters: WorkerParameters,
  private val backupRepository: BackupRepository,
) : CoroutineWorker(appContext, parameters) {
  override suspend fun doWork(): Result {
    if (!GoogleDriveBackupPreferences(applicationContext).isConfigured()) return Result.success()

    return runCatching {
      backupRepository.backupToGoogleDriveNow()
    }.fold(
      onSuccess = { Result.success() },
      onFailure = { error ->
        when {
          error is SecurityException || error is IllegalArgumentException -> Result.failure()
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

class BackupWorkerFactory(
  private val repositoryProvider: () -> BackupRepository,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? =
    if (workerClassName == GoogleDriveBackupWorker::class.java.name) {
      GoogleDriveBackupWorker(appContext, workerParameters, repositoryProvider())
    } else {
      null
    }
}
