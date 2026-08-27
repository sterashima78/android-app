package dev.terashima.yomitorirss

import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkerFactory
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import dev.terashima.yomitorirss.feature.backup.data.BackupWorkerFactory
import dev.terashima.yomitorirss.feature.knowledge.data.KnowledgeWorkerFactory
import dev.terashima.yomitorirss.feature.library.data.LibraryWorkerFactory
import dev.terashima.yomitorirss.feature.mail.data.MailWorkerFactory
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies
import dev.terashima.yomitorirss.feature.summary.data.SummaryWorkerFactory
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetUpdater
import dev.terashima.yomitorirss.feature.widget.data.WidgetWorkerFactory

/**
 * Connects WorkManager-owned entry points to the application-scope dependency graph without
 * requiring Workers to look dependencies up from Application or construct parallel repository graphs.
 */
fun createAppWorkerFactory(container: AppContainer): WorkerFactory =
  DelegatingWorkerFactory().apply {
    addFactory(BackupWorkerFactory { container.backupRepository })
    addFactory(KnowledgeWorkerFactory { container.knowledgeBuildRunner })
    addFactory(MailWorkerFactory { container.mailRepository })
    addFactory(LibraryWorkerFactory { container.libraryWorkerRuntime })
    addFactory(
      WidgetWorkerFactory(
        repositoryProvider = { container.widgetRepository },
        onRefreshComplete = UnreadArticlesWidgetUpdater::updateAll,
      ),
    )
    addFactory(
      SummaryWorkerFactory(
        runtimeProvider = {
          SummaryRuntimeDependencies(
            articleRepository = container.articleRepository,
            bookmarkContentQuery = container.bookmarkContentQuery,
            bookmarkEnrichmentRepository = container.bookmarkEnrichmentRepository,
          )
        },
        articleContentClientProvider = {
          ArticleContentClient(container.httpClient)
        },
        databaseProvider = { container.database },
        textInferenceProvider = { container.textInference },
        cloudInferenceProvider = { container.summaryCloudInference },
        executionSettingsProvider = { container.summaryExecutionSettings },
        runBookmarkAutoEnrichmentBackfill = {
          container.backfillBookmarkAutoEnrichmentUseCase()
        },
      ),
    )
  }
