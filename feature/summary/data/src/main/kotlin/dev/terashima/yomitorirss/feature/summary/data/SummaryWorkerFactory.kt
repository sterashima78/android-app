package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies

class SummaryWorkerFactory(
  private val runtimeProvider: () -> SummaryRuntimeDependencies,
  private val runBookmarkAutoEnrichmentBackfill: suspend () -> Unit,
) : WorkerFactory() {
  private val runtime: SummaryRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
    runtimeProvider,
  )

  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    SummaryWorker::class.java.name -> SummaryWorker(appContext, workerParameters, runtime)
    SummaryContentFetchWorker::class.java.name ->
      SummaryContentFetchWorker(appContext, workerParameters, runtime)
    BookmarkAutoEnrichmentBackfillWorker::class.java.name ->
      BookmarkAutoEnrichmentBackfillWorker(
        appContext,
        workerParameters,
        runBookmarkAutoEnrichmentBackfill,
      )
    else -> null
  }
}
