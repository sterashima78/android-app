package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

  fun ensureScheduled(context: Context) {
    if (GoogleDriveBackupPreferences(context).isConfigured()) {
      schedulePeriodic(context)
    } else {
      cancel(context)
    }
  }

  fun schedulePeriodic(context: Context) {
    val constraints = googleDriveBackupNetworkConstraints(
      wifiOnly = GoogleDriveBackupPreferences(context).isWifiOnly(),
    )
    val request = PeriodicWorkRequestBuilder<GoogleDriveBackupWorker>(1, TimeUnit.DAYS)
      .setConstraints(constraints)
      .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun scheduleAfterChange(context: Context) {
    val preferences = GoogleDriveBackupPreferences(context)
    if (!preferences.isConfigured()) return
    val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>()
      .setInitialDelay(15, TimeUnit.MINUTES)
      .setConstraints(googleDriveBackupNetworkConstraints(preferences.isWifiOnly()))
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

internal fun googleDriveBackupNetworkConstraints(wifiOnly: Boolean): Constraints {
  if (!wifiOnly) {
    return Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
  }

  val wifiRequest = NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .build()
  return Constraints.Builder()
    .setRequiredNetworkRequest(wifiRequest, NetworkType.CONNECTED)
    .build()
}
