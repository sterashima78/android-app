package dev.terashima.yomitorirss

import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkerFactory
import dev.terashima.yomitorirss.feature.backup.data.BackupWorkerFactory
import dev.terashima.yomitorirss.feature.knowledge.data.KnowledgeWorkerFactory
import dev.terashima.yomitorirss.feature.mail.data.MailWorkerFactory
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies
import dev.terashima.yomitorirss.feature.summary.data.SummaryWorkerFactory

/**
 * Connects WorkManager-owned entry points to the application-scope dependency graph without
 * requiring Workers to look dependencies up from Application.
 */
internal fun createAppWorkerFactory(container: AppContainer): WorkerFactory =
  DelegatingWorkerFactory().apply {
    addFactory(BackupWorkerFactory { container.backupRepository })
    addFactory(KnowledgeWorkerFactory { container.knowledgeBuilder })
    addFactory(MailWorkerFactory { container.mailRepository })
    addFactory(
      SummaryWorkerFactory(
        runtimeProvider = {
          SummaryRuntimeDependencies(
            articleRepository = container.articleRepository,
            bookmarkContentQuery = container.bookmarkContentQuery,
            bookmarkEnrichmentRepository = container.bookmarkEnrichmentRepository,
          )
        },
        runBookmarkAutoEnrichmentBackfill = {
          container.backfillBookmarkAutoEnrichmentUseCase()
        },
      ),
    )
  }
