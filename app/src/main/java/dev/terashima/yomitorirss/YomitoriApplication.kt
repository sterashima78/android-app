package dev.terashima.yomitorirss

import android.app.Application
import androidx.work.Configuration
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaProvider
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.data.BookmarkAutoEnrichmentBackfillScheduler
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider

class YomitoriApplication : Application(),
  Configuration.Provider,
  MainActivityDependenciesProvider,
  WidgetRepositoryProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  LanWebRepositoryProvider {
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
  val routeDependencies: AppRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppRouteDependencies(this, container) }
  override val mainActivityDependencies: MainActivityDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MainActivityDependencies(
      routeDependencies = routeDependencies,
      lanWebServerController = container.lanWebServerController,
      saveSharedBookmark = container.saveSharedBookmarkUseCase,
    )
  }
  override val workManagerConfiguration: Configuration by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Configuration.Builder()
      .setWorkerFactory(createAppWorkerFactory(container))
      .build()
  }
  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    UnreadArticlesWidgetRefreshObserver(this, DataChangeNotifier.shared.version)
  }

  override val databaseSchema: DatabaseSchema get() = appDatabaseSchema
  override val widgetRepository: WidgetRepository get() = container.widgetRepository
  override val taskRepository: TaskRepository get() = container.taskRepository
  override val lanWebArticleRepository: ArticleRepository get() = container.articleRepository
  override val lanWebBookmarkRepository: BookmarkRepository get() = container.bookmarkRepository
  override val lanWebFeedRepository: FeedRepository get() = container.feedRepository

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}
