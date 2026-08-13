package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

class DefaultSummaryTaskQueueRepository(
  context: Context,
  private val database: YomitoriDatabase,
) : SummaryTaskQueueRepository {
  private val appContext = context.applicationContext

  override suspend fun listTasks(): List<SummaryQueueTask> =
    database.listSummaryTaskItems().map { item ->
      SummaryQueueTask(
        articleId = item.task.articleId,
        articleTitle = item.articleTitle,
        sourceTitle = item.sourceTitle,
        state = item.task.state.toSummaryQueueTaskState(),
        queuedAt = item.task.queuedAt,
        startedAt = item.task.startedAt,
        finishedAt = item.task.finishedAt,
        error = item.task.error,
        progressStage = item.task.progressStage,
        progressCurrent = item.task.progressCurrent,
        progressTotal = item.task.progressTotal,
      )
    }

  override suspend fun kick() {
    SummaryQueue.kick(appContext)
  }

  override suspend fun stop(articleId: String): Boolean = SummaryQueue.stop(appContext, articleId)

  override suspend fun cancel(articleId: String): Boolean = SummaryQueue.cancel(appContext, articleId)

  override suspend fun resume(articleId: String): Boolean = SummaryQueue.resume(appContext, articleId)
}

private fun String.toSummaryQueueTaskState(): SummaryQueueTaskState = when (this) {
  SUMMARY_QUEUED -> SummaryQueueTaskState.QUEUED
  SUMMARY_RUNNING -> SummaryQueueTaskState.RUNNING
  SUMMARY_COMPLETED -> SummaryQueueTaskState.COMPLETED
  SUMMARY_FAILED -> SummaryQueueTaskState.FAILED
  SUMMARY_STOPPED -> SummaryQueueTaskState.STOPPED
  SUMMARY_CANCELLED -> SummaryQueueTaskState.CANCELLED
  else -> SummaryQueueTaskState.UNKNOWN
}
