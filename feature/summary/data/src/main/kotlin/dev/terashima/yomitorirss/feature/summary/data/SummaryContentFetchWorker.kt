package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SummaryContentFetchWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    if (SummaryQueue.executionState(applicationContext).paused) return Result.success()

    return withContext(Dispatchers.IO) {
      val database = YomitoriDatabase.create(applicationContext)
      val articleContentClient = ArticleContentClient()
      try {
        while (!SummaryQueue.executionState(applicationContext).paused) {
          currentCoroutineContext().ensureActive()
          if (database.countPreparedSummaryArticleContentsForActiveTasks() >= PREFETCH_LIMIT) break

          val article = database.nextSummaryArticleForContentFetch() ?: break
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
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
