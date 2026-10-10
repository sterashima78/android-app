package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.terashima.yomitorirss.feature.backup.BackupScheduleTime
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object GoogleDriveBackupScheduler {
  private const val SCHEDULE_WORK_PREFIX = "google-drive-backup-schedule"
  private const val SCHEDULE_WORK_TAG = "google-drive-backup-schedule"
  private const val BACKUP_WORK_NAME = "google-drive-backup-scheduled-run"
  internal const val KEY_SCHEDULE_HOUR = "schedule_hour"
  internal const val KEY_SCHEDULE_MINUTE = "schedule_minute"

  fun ensureScheduled(context: Context) {
    reschedule(context)
  }

  fun reschedule(context: Context) {
    val appContext = context.applicationContext
    val workManager = WorkManager.getInstance(appContext)
    workManager.cancelAllWorkByTag(SCHEDULE_WORK_TAG)

    val preferences = GoogleDriveBackupPreferences(appContext)
    if (!preferences.isConfigured()) return

    preferences.scheduleTimes().forEach { time ->
      scheduleNext(appContext, time)
    }
  }

  internal fun scheduleNext(context: Context, time: BackupScheduleTime) {
    val appContext = context.applicationContext
    val preferences = GoogleDriveBackupPreferences(appContext)
    if (!preferences.isConfigured() || time !in preferences.scheduleTimes()) return

    val now = ZonedDateTime.now()
    val next = nextBackupOccurrence(now, time)
    val request = OneTimeWorkRequestBuilder<GoogleDriveBackupScheduleWorker>()
      .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
      .setInputData(
        workDataOf(
          KEY_SCHEDULE_HOUR to time.hour,
          KEY_SCHEDULE_MINUTE to time.minute,
        ),
      )
      .addTag(SCHEDULE_WORK_TAG)
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      scheduleWorkName(time, next),
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal fun enqueueBackup(context: Context) {
    val appContext = context.applicationContext
    val preferences = GoogleDriveBackupPreferences(appContext)
    if (!preferences.isConfigured()) return

    val request = OneTimeWorkRequestBuilder<GoogleDriveBackupWorker>()
      .setConstraints(googleDriveBackupNetworkConstraints(preferences.isWifiOnly()))
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      BACKUP_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context.applicationContext).apply {
      cancelUniqueWork(BACKUP_WORK_NAME)
      cancelAllWorkByTag(SCHEDULE_WORK_TAG)
    }
  }

  private fun scheduleWorkName(
    time: BackupScheduleTime,
    occurrence: ZonedDateTime,
  ): String =
    "$SCHEDULE_WORK_PREFIX-${occurrence.toLocalDate()}-${time.encoded.replace(':', '-')}"

  internal fun nextBackupOccurrence(
    now: ZonedDateTime,
    time: BackupScheduleTime,
  ): ZonedDateTime {
    var next = now
      .withHour(time.hour)
      .withMinute(time.minute)
      .withSecond(0)
      .withNano(0)
    if (!next.isAfter(now)) {
      next = next.plusDays(1)
    }
    return next
  }

  internal fun nextBackupDelayMillis(
    now: ZonedDateTime,
    time: BackupScheduleTime,
  ): Long = Duration.between(now, nextBackupOccurrence(now, time)).toMillis()
}

class GoogleDriveBackupScheduleWorker(
  appContext: Context,
  parameters: WorkerParameters,
) : Worker(appContext, parameters) {
  override fun doWork(): Result {
    val time = runCatching {
      BackupScheduleTime(
        hour = inputData.getInt(GoogleDriveBackupScheduler.KEY_SCHEDULE_HOUR, -1),
        minute = inputData.getInt(GoogleDriveBackupScheduler.KEY_SCHEDULE_MINUTE, -1),
      )
    }.getOrNull() ?: return Result.failure()

    val preferences = GoogleDriveBackupPreferences(applicationContext)
    if (!preferences.isConfigured() || time !in preferences.scheduleTimes()) {
      return Result.success()
    }

    GoogleDriveBackupScheduler.enqueueBackup(applicationContext)
    GoogleDriveBackupScheduler.scheduleNext(applicationContext, time)
    return Result.success()
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
