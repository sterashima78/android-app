package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaProvider
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepositoryProvider
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentRepositoryProvider
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepositoryProvider
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider

class YomitoriApplication : Application(),
  WidgetRepositoryProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  BackupRepositoryProvider,
  KnowledgeRepositoryProvider,
  BookmarkEnrichmentRepositoryProvider,
  LanWebRepositoryProvider,
  BookmarkAutoEnrichmentBackfillProvider {
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContainer(this)
  }
  val routeDependencies: AppRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppRouteDependencies(this, container)
  }
  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    UnreadArticlesWidgetRefreshObserver(
      context = this,
      dataChanges = DataChangeNotifier.shared.version,
    )
  }
  private val bookmarkAutoEnrichmentBackfillUseCase: BookmarkAutoEnrichmentBackfillUseCase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    BookmarkAutoEnrichmentBackfillUseCase(
      bookmarkRepository = container.bookmarkRepository,
      summaryRepository = container.summaryRepository,
    )
  }

  override val databaseSchema: DatabaseSchema
    get() = appDatabaseSchema

  override val widgetRepository: WidgetRepository
    get() = container.widgetRepository

  override val taskRepository: TaskRepository
    get() = container.taskRepository

  override val backupRepository: BackupRepository
    get() = container.backupRepository

  override val knowledgeRepository: KnowledgeRepository
    get() = container.knowledgeRepository

  override val bookmarkEnrichmentRepository: BookmarkEnrichmentRepository
    get() = container.bookmarkEnrichmentRepository

  override val lanWebArticleRepository: ArticleRepository
    get() = container.articleRepository

  override val lanWebBookmarkRepository: BookmarkRepository
    get() = container.bookmarkRepository

  override val lanWebFeedRepository: FeedRepository
    get() = container.feedRepository

  override suspend fun runBookmarkAutoEnrichmentBackfill() {
    bookmarkAutoEnrichmentBackfillUseCase()
  }

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}
