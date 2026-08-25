package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
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
  private val articleRepository: ArticleRepository,
  private val bookmarkContentQuery: BookmarkContentQuery,
) : SummaryTaskQueueRepository {
  private val appContext = context.applicationContext

  override suspend fun listTasks(): List<SummaryQueueTask> {
    val tasks = database.listSummaryTasks()
    val articleIds = tasks.mapTo(linkedSetOf(), SummaryTaskRecord::articleId)
    val articles = articleRepository.findArticles(articleIds).associateBy { it.id }
    val highPriorityArticleIds = bookmarkContentQuery.readLaterContentIds(articleIds)
    val executionProvider = SummaryExecutionPreferences(appContext).currentProvider()
    return tasks.map { task ->
      val article = articles[task.articleId]
      SummaryQueueTask(
        articleId = task.articleId,
        articleTitle = article?.title ?: "記事が見つかりません",
        sourceTitle = article?.sourceTitle.orEmpty(),
        state = task.state.toSummaryQueueTaskState(),
        queuedAt = task.queuedAt,
        startedAt = task.startedAt,
        finishedAt = task.finishedAt,
        error = task.error,
        priority = if (task.articleId in highPriorityArticleIds) SummaryQueueTaskPriority.HIGH else SummaryQueueTaskPriority.NORMAL,
        progressStage = task.progressStage.toSummaryQueueTaskProgressStage(),
        progressCurrent = task.progressCurrent,
        progressTotal = task.progressTotal,
        executionProvider = executionProvider,
      )
    }
  }

  override suspend fun taskCounts(): SummaryQueueTaskCounts = database.countSummaryQueueTasks()
  override suspend fun executionState(): SummaryQueueExecutionState = SummaryQueue.executionState(appContext)
  override suspend fun kick() = SummaryQueue.kick(appContext)
  override suspend fun setLocalPaused(paused: Boolean) = SummaryQueue.setLocalPaused(appContext, paused)
  override suspend fun setCloudPaused(paused: Boolean) = SummaryQueue.setCloudPaused(appContext, paused)
  override suspend fun setResumeLocalWhenCharging(enabled: Boolean) =
    SummaryQueue.setResumeLocalWhenCharging(appContext, enabled)
  override suspend fun stop(articleId: String): Boolean = SummaryQueue.stop(appContext, articleId)
  override suspend fun cancel(articleId: String): Boolean = SummaryQueue.cancel(appContext, articleId)
  override suspend fun resume(articleId: String): Boolean = SummaryQueue.resume(appContext, articleId)

  override suspend fun retryFailedBookmarkTasks(): Int {
    val failedIds = database.listFailedSummaryTaskIds()
    val bookmarkedIds = bookmarkContentQuery.bookmarkedContentIds(failedIds)
    val retried = database.requeueFailedSummaryTasks(bookmarkedIds)
    SummaryQueue.kick(appContext)
    return retried
  }
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
  SUMMARY_PROGRESS_CLOUD_GENERATING_SUMMARY -> SummaryQueueTaskProgressStage.CLOUD_GENERATING_SUMMARY
  SUMMARY_PROGRESS_CLOUD_GENERATING_METADATA -> SummaryQueueTaskProgressStage.CLOUD_GENERATING_METADATA
  else -> SummaryQueueTaskProgressStage.UNKNOWN
}
