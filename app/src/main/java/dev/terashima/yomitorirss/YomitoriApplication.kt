package dev.terashima.yomitorirss

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import java.lang.ref.WeakReference

class YomitoriApplication : Application(),
  Configuration.Provider,
  MainActivityDependenciesProvider,
  WidgetRepositoryProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  LanWebRepositoryProvider {
  private val resumedActivityTracker = ResumedActivityTracker()
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContainer(this, resumedActivityTracker::current)
  }
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
    registerActivityLifecycleCallbacks(resumedActivityTracker)
    StartupCrashStore.install(this)
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}

private class ResumedActivityTracker : Application.ActivityLifecycleCallbacks {
  private var resumedActivity = WeakReference<Activity>(null)

  fun current(): Activity? = resumedActivity.get()
    ?.takeUnless(Activity::isFinishing)
    ?.takeUnless(Activity::isDestroyed)

  override fun onActivityResumed(activity: Activity) {
    resumedActivity = WeakReference(activity)
  }

  override fun onActivityPaused(activity: Activity) {
    if (resumedActivity.get() === activity) resumedActivity.clear()
  }

  override fun onActivityDestroyed(activity: Activity) {
    if (resumedActivity.get() === activity) resumedActivity.clear()
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivityStopped(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
