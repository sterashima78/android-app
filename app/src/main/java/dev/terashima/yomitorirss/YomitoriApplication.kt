package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaProvider
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepositoryProvider
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuilder
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepositoryProvider
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import dev.terashima.yomitorirss.feature.library.WebLibraryMutatorProvider
import dev.terashima.yomitorirss.feature.mail.MailRepository
import dev.terashima.yomitorirss.feature.mail.MailRepositoryProvider
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.BookmarkAutoEnrichmentBackfillProvider
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependenciesProvider
import dev.terashima.yomitorirss.feature.summary.data.BookmarkAutoEnrichmentBackfillScheduler
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider

class YomitoriApplication : Application(),
  MainActivityDependenciesProvider,
  WidgetRepositoryProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  BackupRepositoryProvider,
  KnowledgeRepositoryProvider,
  MailRepositoryProvider,
  LanWebRepositoryProvider,
  SummaryRuntimeDependenciesProvider,
  BookmarkAutoEnrichmentBackfillProvider,
  WebLibraryMutatorProvider {
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
  val routeDependencies: AppRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppRouteDependencies(this, container) }
  override val mainActivityDependencies: MainActivityDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MainActivityDependencies(
      routeDependencies = routeDependencies,
      lanWebServerController = container.lanWebServerController,
      saveSharedBookmark = container.saveSharedBookmarkUseCase,
    )
  }
  override val webLibraryMutator: WebLibraryMutator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    NotifyingWebLibraryMutator(
      delegate = container.featureRuntimeDependencies.library.webLibraryMutator,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
  }
  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    UnreadArticlesWidgetRefreshObserver(this, DataChangeNotifier.shared.version)
  }

  override val databaseSchema: DatabaseSchema get() = appDatabaseSchema
  override val widgetRepository: WidgetRepository get() = container.widgetRepository
  override val taskRepository: TaskRepository get() = container.taskRepository
  override val backupRepository: BackupRepository get() = container.backupRepository
  override val knowledgeRepository: KnowledgeRepository get() = container.knowledgeRepository
  override val knowledgeBuilder: KnowledgeBuilder get() = container.knowledgeBuilder
  override val mailRepository: MailRepository get() = container.mailRepository
  override val summaryRuntimeDependencies: SummaryRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryRuntimeDependencies(container.articleRepository, container.bookmarkContentQuery, container.bookmarkEnrichmentRepository)
  }
  override val lanWebArticleRepository: ArticleRepository get() = container.articleRepository
  override val lanWebBookmarkRepository: BookmarkRepository get() = container.bookmarkRepository
  override val lanWebFeedRepository: FeedRepository get() = container.feedRepository

  override suspend fun runBookmarkAutoEnrichmentBackfill() {
    container.backfillBookmarkAutoEnrichmentUseCase()
  }

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}
