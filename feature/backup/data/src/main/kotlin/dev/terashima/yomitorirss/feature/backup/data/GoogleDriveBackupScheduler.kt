package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object GoogleDriveBackupScheduler {
  private const val PERIODIC_WORK_NAME = "google-drive-backup-periodic"
  private const val CHANGE_WORK_NAME = "google-drive-backup-after-change"

  private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

  fun ensureScheduled(context: Context) {
    if (GoogleDriveBackupPreferences(context).isConfigured()) {
      schedulePeriodic(context)
    } else {
      cancel(context)
    }
  }

  fun schedulePeriodic(context: Context) {
    val request = PeriodicWorkRequestBuilder<GoogleDriveBackupWorker>(1, TimeUnit.DAYS)
      .setConstraints(networkConstraints)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun scheduleAfterChange(context: Context) {
    if (!GoogleDriveBackupPreferences(context).isConfigured()) return
    val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>()
      .setInitialDelay(15, TimeUnit.MINUTES)
      .setConstraints(networkConstraints)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      CHANGE_WORK_NAME,
      ExistingWorkPolicy.REPLACE,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context.applicationContext).apply {
      cancelUniqueWork(PERIODIC_WORK_NAME)
      cancelUniqueWork(CHANGE_WORK_NAME)
    }
  }
}
