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
  private const val INFERENCE_QUEUE_NAME = "article-summary-queue"
  private const val CONTENT_FETCH_QUEUE_NAME = "article-summary-content-fetch-queue"
  private const val INFERENCE_TAG = "article-summary"
  private const val CONTENT_FETCH_TAG = "article-summary-content-fetch"
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
      schedulePipelineWorkers(appContext)
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
    runCatching { schedulePipelineWorkers(appContext) }
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
    schedulePipelineWorkers(appContext)
  }

  internal fun kickInference(context: Context) {
    val appContext = context.applicationContext
    if (SummaryQueueExecutionPreferences(appContext).paused) return
    scheduleInferenceWorker(appContext)
  }

  internal fun kickContentFetch(context: Context) {
    val appContext = context.applicationContext
    if (SummaryQueueExecutionPreferences(appContext).paused) return
    scheduleContentFetchWorker(appContext)
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
      if (paused) {
        ensureResumeOnChargingScheduled(appContext)
      } else {
        ensureCleanupScheduled(appContext)
        schedulePipelineWorkers(appContext)
      }
      return
    }

    if (paused) {
      preferences.paused = true
      try {
        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelUniqueWork(INFERENCE_QUEUE_NAME).result.get()
        workManager.cancelUniqueWork(CONTENT_FETCH_QUEUE_NAME).result.get()
        requeueInterruptedTasks(appContext)
        ensureResumeOnChargingScheduled(appContext)
      } catch (error: Throwable) {
        preferences.paused = false
        runCatching { schedulePipelineWorkers(appContext) }
        throw error
      }
    } else {
      WorkManager.getInstance(appContext).cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      preferences.paused = false
      try {
        ensureCleanupScheduled(appContext)
        schedulePipelineWorkers(appContext)
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
    if (!preferences.resumeWhenCharging) return

    // Library organization shares this execution gate and has its own charging worker. Either
    // worker may clear the gate first, so scheduling the summary queue must remain idempotent.
    preferences.paused = false
    try {
      ensureCleanupScheduled(appContext)
      schedulePipelineWorkers(appContext)
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

    if (previousState == SUMMARY_RUNNING) restartInferenceWorker(appContext)
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

    if (previousState == SUMMARY_RUNNING) restartInferenceWorker(appContext)
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
    schedulePipelineWorkers(appContext)
    return true
  }

  private fun restartInferenceWorker(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(INFERENCE_QUEUE_NAME).result.get()
    scheduleInferenceWorker(context)
  }

  private fun schedulePipelineWorkers(context: Context) {
    if (SummaryQueueExecutionPreferences(context).paused) {
      ensureResumeOnChargingScheduled(context)
      return
    }
    scheduleContentFetchWorker(context)
    scheduleInferenceWorker(context)
  }

  private fun scheduleInferenceWorker(context: Context) {
    val request = OneTimeWorkRequestBuilder<SummaryWorker>()
      .addTag(INFERENCE_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      INFERENCE_QUEUE_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  private fun scheduleContentFetchWorker(context: Context) {
    val request = OneTimeWorkRequestBuilder<SummaryContentFetchWorker>()
      .addTag(CONTENT_FETCH_TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      CONTENT_FETCH_QUEUE_NAME,
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
