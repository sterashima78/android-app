package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRequestResult

class DefaultSummaryRepository(
  context: Context,
  private val database: YomitoriDatabase,
  private val modelManager: LocalModelManager,
) : SummaryRepository {
  private val appContext = context.applicationContext

  override suspend fun request(articleId: String, forceRefresh: Boolean): SummaryRequestResult {
    if (!forceRefresh) {
      database.findSummary(articleId)?.let { saved ->
        return SummaryRequestResult.Cached(saved.summary)
      }

      database.findSummaryTask(articleId)?.let { task ->
        when (task.state) {
          SUMMARY_QUEUED,
          SUMMARY_RUNNING -> return SummaryRequestResult.Processing

          SUMMARY_FAILED -> return SummaryRequestResult.PreviousFailure(task.error ?: "不明なエラー")
        }
      }
    }

    modelManager.selectedModel() ?: error("要約モデルをダウンロードして選択してください")
    return SummaryRequestResult.Enqueued(
      accepted = SummaryQueue.enqueue(
        context = appContext,
        articleId = articleId,
        forceRefresh = forceRefresh,
      ),
      forceRefresh = forceRefresh,
    )
  }
}
