package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies

class SummaryWorkerFactory(
  private val runtimeProvider: () -> SummaryRuntimeDependencies,
  private val articleContentClientProvider: () -> ArticleContentClient,
  private val databaseProvider: () -> YomitoriDatabase,
  private val textInferenceProvider: () -> AiTextInference,
  private val runBookmarkAutoEnrichmentBackfill: suspend () -> Unit,
) : WorkerFactory() {
  private val runtime: SummaryRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
    runtimeProvider,
  )
  private val articleContentClient: ArticleContentClient by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
    articleContentClientProvider,
  )
  private val database: YomitoriDatabase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
    databaseProvider,
  )
  private val textInference: AiTextInference by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
    textInferenceProvider,
  )

  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    SummaryWorker::class.java.name ->
      SummaryWorker(appContext, workerParameters, runtime, database, textInference)
    SummaryContentFetchWorker::class.java.name ->
      SummaryContentFetchWorker(
        appContext,
        workerParameters,
        runtime,
        articleContentClient,
        database,
        textInference,
      )
    SummaryTaskLogCleanupWorker::class.java.name ->
      SummaryTaskLogCleanupWorker(appContext, workerParameters, database)
    BookmarkAutoEnrichmentBackfillWorker::class.java.name ->
      BookmarkAutoEnrichmentBackfillWorker(
        appContext,
        workerParameters,
        runBookmarkAutoEnrichmentBackfill,
      )
    else -> null
  }
}
