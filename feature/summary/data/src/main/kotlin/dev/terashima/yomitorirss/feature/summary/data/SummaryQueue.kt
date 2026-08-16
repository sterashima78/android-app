package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import java.util.concurrent.TimeUnit

object SummaryQueue {
  private const val QUEUE_NAME = "article-summary-queue"
  private const val TAG = "article-summary"
  private const val CLEANUP_WORK_NAME = "article-summary-task-log-cleanup"
  private const val RESUME_ON_CHARGING_WORK_NAME = "article-summary-resume-on-charging"

  fun enqueue(context: Context, articleId: String, forceRefresh: Boolean): Boolean {
    val appContext = context.applicationContext
    val database = YomitoriDatabase.create(appContext)
    val accepted = try {
      database.enqueueSummaryTask(articleId, forceRefresh)
    } finally {
      database.close()
    }
    if (!accepted) return false

    ensureCleanupScheduled(appContext)
    return runCatching {
      scheduleWorker(appContext)
      true
    }.getOrElse { error ->
      val retryDatabase = YomitoriDatabase.create(appContext)
      try {
        retryDatabase.markSummaryTaskFailed(articleId, error.message ?: error.javaClass.simpleName)
      } finally {
        retryDatabase.close()
      }
      throw error
    }
  }

  fun enqueueMissingBookmarkEnrichment(context: Context, articleIds: List<String>): Int {
    val appContext = context.applicationContext
    val acceptedIds = mutableListOf<String>()
    val database = YomitoriDatabase.create(appContext)
    try {
      articleIds.asSequence().distinct().forEach { articleId ->
        if (database.findSummary(articleId) != null) return@forEach
        if (database.findSummaryTask(articleId) != null) return@forEach
        if (database.enqueueSummaryTask(articleId, forceRefresh = false)) acceptedIds += articleId
      }
    } finally {
      database.close()
    }
    if (acceptedIds.isEmpty()) return 0

    ensureCleanupScheduled(appContext)
    runCatching { scheduleWorker(appContext) }
      .onFailure { error ->
        val retryDatabase = YomitoriDatabase.create(appContext)
        try {
          acceptedIds.forEach { articleId ->
            retryDatabase.markSummaryTaskFailed(articleId, error.message ?: error.javaClass.simpleName)
          }
        } finally {
          retryDatabase.close()
        }
      }
      .getOrThrow()
    return acceptedIds.size
  }

  fun kick(context: Context) {
    val appContext = context.applicationContext
    ensureCleanupScheduled(appContext)
    scheduleWorker(appContext)
  }

  fun executionState(context: Context): SummaryQueueExecutionState {
    val preferences = SummaryQueueExecutionPreferences(context)
    return SummaryQueueExecutionState(
      paused = preferences.paused,
      resumeWhenCharging = preferences.resumeWhenCharging,
    )
  }

  fun setPaused(context: Context, paused: Boolean) {
    val appContext = context.applicationContext
    val preferences = SummaryQueueExecutionPreferences(appContext)
    if (preferences.paused == paused) {
      if (paused) ensureResumeOnChargingScheduled(appContext)
      return
    }

    if (paused) {
      preferences.paused = true
      try {
        WorkManager.getInstance(appContext).cancelUniqueWork(QUEUE_NAME).result.get()
        requeueInterruptedTasks(appContext)
        ensureResumeOnChargingScheduled(appContext)
      } catch (error: Throwable) {
        preferences.paused = false
        runCatching { scheduleWorker(appContext) }
        throw error
      }
    } else {
      WorkManager.getInstance(appContext).cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      preferences.paused = false
      try {
        ensureCleanupScheduled(appContext)
        scheduleWorker(appContext)
      } catch (error: Throwable) {
        preferences.paused = true
        ensureResumeOnChargingScheduled(appContext)
        throw error
      }
    }
  }

  fun setResumeWhenCharging(context: Context, enabled: Boolean) {
    val appContext = context.applicationContext
    val preferences = SummaryQueueExecutionPreferences(appContext)
    preferences.resumeWhenCharging = enabled
    if (!preferences.paused) return

    if (enabled) {
      ensureResumeOnChargingScheduled(appContext)
    } else {
      WorkManager.getInstance(appContext).cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
    }
  }

  internal fun resumeAutomaticallyWhenCharging(context: Context) {
    val appContext = context.applicationContext
    val preferences = SummaryQueueExecutionPreferences(appContext)
    if (!preferences.paused || !preferences.resumeWhenCharging) return

    preferences.paused = false
    try {
      ensureCleanupScheduled(appContext)
      scheduleWorker(appContext)
    } catch (error: Throwable) {
      preferences.paused = true
      throw error
    }
  }

  fun stop(context: Context, articleId: String): Boolean {
    val appContext = context.applicationContext
    val database = YomitoriDatabase.create(appContext)
    val previousState = try {
      database.stopSummaryTask(articleId)
    } finally {
      database.close()
    } ?: return false

    if (previousState == SUMMARY_RUNNING) restartWorker(appContext)
    return true
  }

  fun cancel(context: Context, articleId: String): Boolean {
    val appContext = context.applicationContext
    val database = YomitoriDatabase.create(appContext)
    val previousState = try {
      database.cancelSummaryTask(articleId)
    } finally {
      database.close()
    } ?: return false

    if (previousState == SUMMARY_RUNNING) restartWorker(appContext)
    return true
  }

  fun resume(context: Context, articleId: String): Boolean {
    val appContext = context.applicationContext
    val database = YomitoriDatabase.create(appContext)
    val resumed = try {
      database.resumeSummaryTask(articleId)
    } finally {
      database.close()
    }
    if (!resumed) return false
    ensureCleanupScheduled(appContext)
    scheduleWorker(appContext)
    return true
  }

  private fun restartWorker(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(QUEUE_NAME).result.get()
    scheduleWorker(context)
  }

  private fun scheduleWorker(context: Context) {
    if (SummaryQueueExecutionPreferences(context).paused) {
      ensureResumeOnChargingScheduled(context)
      return
    }

    val request = OneTimeWorkRequestBuilder<SummaryWorker>()
      .addTag(TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      QUEUE_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  private fun ensureResumeOnChargingScheduled(context: Context) {
    val preferences = SummaryQueueExecutionPreferences(context)
    if (!preferences.paused || !preferences.resumeWhenCharging) return

    runCatching {
      val request = OneTimeWorkRequestBuilder<SummaryResumeOnChargingWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiresCharging(true)
            .build(),
        )
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(
        RESUME_ON_CHARGING_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        request,
      )
    }
  }

  private fun requeueInterruptedTasks(context: Context) {
    val database = YomitoriDatabase.create(context)
    try {
      database.requeueInterruptedSummaryTasks()
    } finally {
      database.close()
    }
  }

  private fun ensureCleanupScheduled(context: Context) {
    runCatching { scheduleCleanup(context) }
  }

  private fun scheduleCleanup(context: Context) {
    val request = PeriodicWorkRequestBuilder<SummaryTaskLogCleanupWorker>(1, TimeUnit.DAYS)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      CLEANUP_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }
}
