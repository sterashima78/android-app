package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionSettings
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRequestResult

class DefaultSummaryRepository(
  context: Context,
  private val database: YomitoriDatabase,
  private val textInference: AiTextInference,
  private val executionSettings: SummaryExecutionSettings,
  private val cloudInference: SummaryCloudInference,
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

    requireExecutionProviderAvailable()
    return SummaryRequestResult.Enqueued(
      accepted = SummaryQueue.enqueue(
        context = appContext,
        articleId = articleId,
        forceRefresh = forceRefresh,
      ),
      forceRefresh = forceRefresh,
    )
  }

  override suspend fun requestBookmarkEnrichment(articleId: String): SummaryRequestResult {
    requireExecutionProviderAvailable()
    return SummaryRequestResult.Enqueued(
      accepted = SummaryQueue.enqueue(
        context = appContext,
        articleId = articleId,
        forceRefresh = false,
      ),
      forceRefresh = false,
    )
  }

  override suspend fun enqueueMissingBookmarkEnrichment(articleIds: List<String>): Int {
    if (articleIds.isEmpty() || !executionProviderAvailable()) return 0
    return SummaryQueue.enqueueMissingBookmarkEnrichment(appContext, articleIds)
  }

  override suspend fun findSummary(articleId: String): String? =
    database.findSummary(articleId)?.summary

  private fun requireExecutionProviderAvailable() {
    when (executionSettings.currentProvider()) {
      SummaryExecutionProvider.LOCAL ->
        textInference.selectedModel() ?: error("要約モデルをダウンロードして選択してください")
      SummaryExecutionProvider.CHATGPT ->
        check(cloudInference.isAvailable()) { "ChatGPTへ接続し、利用モデルを選択してください" }
    }
  }

  private fun executionProviderAvailable(): Boolean = when (executionSettings.currentProvider()) {
    SummaryExecutionProvider.LOCAL -> textInference.selectedModel() != null
    SummaryExecutionProvider.CHATGPT -> cloudInference.isAvailable()
  }
}
