package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependenciesProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SummaryContentFetchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
  private val runtime: SummaryRuntimeDependencies by lazy(LazyThreadSafetyMode.NONE) {
    (applicationContext as? SummaryRuntimeDependenciesProvider)?.summaryRuntimeDependencies
      ?: error("Application must provide SummaryRuntimeDependencies")
  }

  override suspend fun doWork(): Result {
    if (SummaryQueue.executionState(applicationContext).paused) return Result.success()
    return withContext(Dispatchers.IO) {
      val database = YomitoriDatabase.create(applicationContext)
      val articleContentClient = ArticleContentClient()
      val modelManager = LocalModelManager.shared(applicationContext)
      try {
        while (!SummaryQueue.executionState(applicationContext).paused) {
          currentCoroutineContext().ensureActive()
          if (database.countPreparedSummaryArticleContentsForActiveTasks() >= PREFETCH_LIMIT) break
          val candidates = database.listSummaryContentFetchCandidates()
          if (candidates.isEmpty()) break
          val highPriorityIds = runtime.bookmarkContentQuery.readLaterContentIds(
            candidates.mapTo(linkedSetOf(), SummaryTaskRecord::articleId),
          )
          val task = selectNextSummaryTask(candidates, highPriorityIds) ?: break
          val article = runtime.articleRepository.findArticle(task.articleId)
          if (article == null) {
            database.failQueuedSummaryTask(task.articleId, "記事が見つかりません")
            continue
          }
          if (modelManager.selectedModel() == null) {
            database.failQueuedSummaryTask(article.id, MODEL_NOT_SELECTED_MESSAGE)
            continue
          }
          database.updateQueuedSummaryTaskProgress(article.id, SUMMARY_PROGRESS_FETCHING_ARTICLE)
          try {
            val articleText = articleContentClient.fetchArticleText(article.url)
            currentCoroutineContext().ensureActive()
            if (database.savePreparedSummaryArticleContentIfQueued(article.id, articleText)) {
              SummaryQueue.kickInference(applicationContext)
            }
          } catch (error: CancellationException) {
            throw error
          } catch (error: Throwable) {
            database.failQueuedSummaryTask(article.id, error.userMessage())
          }
        }
        Result.success()
      } finally {
        database.close()
      }
    }
  }

  companion object {
    internal const val PREFETCH_LIMIT = 2
    internal const val MODEL_NOT_SELECTED_MESSAGE = "要約モデルをダウンロードして選択してください"
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }.mapNotNull(Throwable::message).firstOrNull(String::isNotBlank) ?: javaClass.simpleName
