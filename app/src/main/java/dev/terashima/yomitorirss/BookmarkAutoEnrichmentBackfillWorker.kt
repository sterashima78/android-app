package dev.terashima.yomitorirss

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object BookmarkAutoEnrichmentBackfillScheduler {
  private const val WORK_NAME = "bookmark-auto-enrichment-backfill"

  fun schedule(context: Context) {
    val request = OneTimeWorkRequestBuilder<BookmarkAutoEnrichmentBackfillWorker>().build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }
}

interface BookmarkAutoEnrichmentBackfillProvider {
  suspend fun runBookmarkAutoEnrichmentBackfill()
}

internal class BookmarkAutoEnrichmentBackfillUseCase(
  private val bookmarkRepository: BookmarkRepository,
  private val summaryRepository: SummaryRepository,
) {
  suspend operator fun invoke() {
    val articleIds = bookmarkRepository.listAllSavedArticles()
      .asSequence()
      .map { it.article }
      .filter { article ->
        shouldRequestBookmarkEnrichment(
          url = article.url,
          sourceFeedUrl = article.sourceFeedUrl,
          contentType = article.effectiveContentType,
        )
      }
      .map { it.id }
      .toList()

    summaryRepository.enqueueMissingBookmarkEnrichment(articleIds)
  }
}

internal class BookmarkAutoEnrichmentBackfillWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val provider = applicationContext as? BookmarkAutoEnrichmentBackfillProvider
      ?: return@withContext Result.failure()

    runCatching { provider.runBookmarkAutoEnrichmentBackfill() }.fold(
      onSuccess = { Result.success() },
      onFailure = { Result.retry() },
    )
  }
}
