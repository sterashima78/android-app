package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaProvider
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.BackupRepositoryProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepositoryProvider
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.widget.TaskRepositoryProvider
import dev.terashima.yomitorirss.feature.widget.UnreadArticlesWidgetRefreshObserver
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepositoryProvider
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.XViewerCssRepositoryProvider
import dev.terashima.yomitorirss.feature.x.data.SharedPreferencesXViewerCssRepository

class YomitoriApplication : Application(),
  WidgetRepositoryProvider,
  TaskRepositoryProvider,
  DatabaseSchemaProvider,
  BackupRepositoryProvider,
  KnowledgeRepositoryProvider,
  XViewerCssRepositoryProvider {
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

  override val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SharedPreferencesXViewerCssRepository(this)
  }

  override fun onCreate() {
    super.onCreate()
    StartupCrashStore.install(this)
    unreadArticlesWidgetRefreshObserver.start()
    runCatching { BookmarkAutoEnrichmentBackfillScheduler.schedule(this) }
  }
}
