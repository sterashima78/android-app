package dev.terashima.yomitorirss

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.work.Configuration
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaProvider
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import dev.terashima.yomitorirss.diagnostics.Android17MemoryAnomalyProfiler
import dev.terashima.yomitorirss.diagnostics.AppLocalAiMemoryMonitor
import dev.terashima.yomitorirss.diagnostics.StartupCrashStore
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.backup.data.AndroidBackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.data.BackupPreferenceChangeObserver
import dev.terashima.yomitorirss.feature.backup.data.DatabaseBackupChangeObserver
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.data.BookmarkAutoEnrichmentBackfillScheduler
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.web.LanWebRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import dev.terashima.yomitorirss.feature.widget.WidgetRefreshScheduler
import dev.terashima.yomitorirss.feature.widget.WidgetRefreshSchedulerProvider
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.filter

class YomitoriApplication : Application(),
  Configuration.Provider,
  MainActivityDependenciesProvider,
  WidgetRepositoryProvider,
  WidgetRefreshSchedulerProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  LanWebRepositoryProvider {
  private val currentActivityTracker = CurrentActivityTracker()
  val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContainer(this, currentActivityTracker::current)
  }
  val routeDependencies: AppRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppRouteDependencies(this, container) }
  override val mainActivityDependencies: MainActivityDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MainActivityDependencies(
      routeDependencies = routeDependencies,
      lanWebServerController = container.lanWebServerController,
      saveSharedBookmark = container.saveSharedBookmarkUseCase,
      addSharedWebBookCapability = container.libraryRuntime.webLibraryMutator::addWebBook,
    )
  }
  override val workManagerConfiguration: Configuration by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Configuration.Builder()
      .setWorkerFactory(createAppWorkerFactory(container))
      .build()
  }
  private val databaseBackupChangeObserver: DatabaseBackupChangeObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DatabaseBackupChangeObserver(
      dataChanges = PersistenceChangeNotifier.shared.version.filter { it > 0L },
      scheduler = AndroidBackupChangeScheduler(this),
    )
  }
  private val backupPreferenceChangeObserver: BackupPreferenceChangeObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackupPreferenceChangeObserver(this, PersistenceChangeNotifier.shared)
  }
  private val unreadArticlesWidgetRefreshObserver: UnreadArticlesWidgetRefreshObserver by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    UnreadArticlesWidgetRefreshObserver(this, DataChangeNotifier.shared.version)
  }

  override val databaseSchema: DatabaseSchema get() = appDatabaseSchema
  override val widgetRepository: WidgetRepository get() = container.widgetRepository
  override val widgetRefreshScheduler: WidgetRefreshScheduler get() = container.widgetRefreshScheduler
  override val taskRepository: TaskRepository get() = container.taskRepository
  override val lanWebArticleRepository: ArticleRepository get() = container.articleRepository
  override val lanWebBookmarkRepository: BookmarkRepository get() = container.bookmarkRepository
  override val lanWebFeedRepository: FeedRepository get() = container.feedRepository

  override fun onCreate() {
    super.onCreate()
    if (!shouldInitializeMainProcessRuntime(Application.getProcessName(), packageName)) return
    registerActivityLifecycleCallbacks(currentActivityTracker)
    Android17MemoryAnomalyProfiler.install(this)
    StartupCrashStore.install(this)
    AppLocalAiMemoryMonitor.install(this)
    databaseBackupChangeObserver.start()
    backupPreferenceChangeObserver.start()
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}

internal fun shouldInitializeMainProcessRuntime(
  processName: String,
  packageName: String,
): Boolean = processName == packageName

private class CurrentActivityTracker : Application.ActivityLifecycleCallbacks {
  private var currentActivity = WeakReference<Activity>(null)

  fun current(): Activity? = currentActivity.get()
    ?.takeUnless(Activity::isFinishing)
    ?.takeUnless(Activity::isDestroyed)

  override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
    currentActivity = WeakReference(activity)
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (currentActivity.get() == null) currentActivity = WeakReference(activity)
  }

  override fun onActivityResumed(activity: Activity) {
    currentActivity = WeakReference(activity)
  }

  override fun onActivityPaused(activity: Activity) {
    if (currentActivity.get() === activity) currentActivity.clear()
  }

  override fun onActivityStopped(activity: Activity) {
    if (currentActivity.get() === activity) currentActivity.clear()
  }

  override fun onActivityDestroyed(activity: Activity) {
    if (currentActivity.get() === activity) currentActivity.clear()
  }

  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
