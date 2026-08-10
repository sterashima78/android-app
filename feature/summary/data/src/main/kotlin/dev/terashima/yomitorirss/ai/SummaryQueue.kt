package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase

object SummaryQueue {
  private const val QUEUE_NAME = "article-summary-queue"
  private const val TAG = "article-summary"

  fun enqueue(context: Context, articleId: String, forceRefresh: Boolean): Boolean {
    val appContext = context.applicationContext
    val database = YomitoriDatabase.create(appContext)
    val accepted = try {
      database.enqueueSummaryTask(articleId, forceRefresh)
    } finally {
      database.close()
    }
    if (!accepted) return false

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

  fun kick(context: Context) {
    scheduleWorker(context.applicationContext)
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
    scheduleWorker(appContext)
    return true
  }

  private fun restartWorker(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(QUEUE_NAME).result.get()
    scheduleWorker(context)
  }

  private fun scheduleWorker(context: Context) {
    val request = OneTimeWorkRequestBuilder<SummaryWorker>()
      .addTag(TAG)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
      QUEUE_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }
}
