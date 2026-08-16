package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskCounts
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskPriority
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository

class DefaultSummaryTaskQueueRepository(
  context: Context,
  private val database: YomitoriDatabase,
) : SummaryTaskQueueRepository {
  private val appContext = context.applicationContext

  override suspend fun listTasks(): List<SummaryQueueTask> {
    val items = database.listSummaryTaskItems()
    val highPriorityArticleIds = database.readLaterSummaryTaskIds(items.map { it.task.articleId })
    return items.map { item ->
      SummaryQueueTask(
        articleId = item.task.articleId,
        articleTitle = item.articleTitle,
        sourceTitle = item.sourceTitle,
        state = item.task.state.toSummaryQueueTaskState(),
        queuedAt = item.task.queuedAt,
        startedAt = item.task.startedAt,
        finishedAt = item.task.finishedAt,
        error = item.task.error,
        priority = if (item.task.articleId in highPriorityArticleIds) {
          SummaryQueueTaskPriority.HIGH
        } else {
          SummaryQueueTaskPriority.NORMAL
        },
        progressStage = item.task.progressStage.toSummaryQueueTaskProgressStage(),
        progressCurrent = item.task.progressCurrent,
        progressTotal = item.task.progressTotal,
      )
    }
  }

  override suspend fun taskCounts(): SummaryQueueTaskCounts = database.countSummaryQueueTasks()

  override suspend fun executionState(): SummaryQueueExecutionState =
    SummaryQueue.executionState(appContext)

  override suspend fun kick() {
    SummaryQueue.kick(appContext)
  }

  override suspend fun setPaused(paused: Boolean) {
    SummaryQueue.setPaused(appContext, paused)
  }

  override suspend fun setResumeWhenCharging(enabled: Boolean) {
    SummaryQueue.setResumeWhenCharging(appContext, enabled)
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

private fun String?.toSummaryQueueTaskProgressStage(): SummaryQueueTaskProgressStage? = when (this) {
  null -> null
  SUMMARY_PROGRESS_FETCHING_ARTICLE -> SummaryQueueTaskProgressStage.FETCHING_ARTICLE
  SUMMARY_PROGRESS_PREPARING_MODEL -> SummaryQueueTaskProgressStage.PREPARING_MODEL
  SUMMARY_PROGRESS_GENERATING_SUMMARY -> SummaryQueueTaskProgressStage.GENERATING_SUMMARY
  SUMMARY_PROGRESS_SUMMARIZING_CHUNK -> SummaryQueueTaskProgressStage.SUMMARIZING_CHUNK
  SUMMARY_PROGRESS_REDUCING_SUMMARY -> SummaryQueueTaskProgressStage.REDUCING_SUMMARY
  SUMMARY_PROGRESS_FINALIZING_SUMMARY -> SummaryQueueTaskProgressStage.FINALIZING_SUMMARY
  else -> SummaryQueueTaskProgressStage.UNKNOWN
}
