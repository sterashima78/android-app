package dev.terashima.yomitorirss

import android.app.Activity
import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.core.network.HttpClient

/**
 * Application-scope composition facade.
 *
 * Concrete feature graphs are assembled by focused runtime dependency groups. Keeping this facade
 * preserves the existing application-scope lifetime and caller API without turning route code into
 * a service locator or introducing a DI framework.
 */
class AppContainer(
  private val application: Application,
  private val resumedActivityProvider: () -> Activity? = { null },
) {
  private val dataChanges = DataChangeNotifier.shared

  internal val httpClient: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HttpClient.create()
  }

  val database: YomitoriDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YomitoriDatabase.create(application)
  }

  internal val databaseConnection: DatabaseConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DatabaseConnection(database)
  }

  internal val featureRuntimeDependencies: AppFeatureRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    AppFeatureRuntimeDependencies(
      application = application,
      database = databaseConnection,
      httpClient = httpClient,
      modelManagerProvider = { aiCoreRuntime.modelManager },
      resumedActivityProvider = resumedActivityProvider,
    )
  }

  private val aiCoreRuntime: AppAiCoreRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppAiCoreRuntimeDependencies(application, database)
  }

  private val contentRuntime: AppContentRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppContentRuntimeDependencies(
      application = application,
      database = databaseConnection,
      dataChanges = dataChanges,
      httpClient = httpClient,
      summaryRepository = aiCoreRuntime.summaryRepository,
    )
  }

  private val supportingRuntime: AppSupportingRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    AppSupportingRuntimeDependencies(
      application = application,
      database = database,
      databaseConnection = databaseConnection,
      dataChanges = dataChanges,
      httpClient = httpClient,
    )
  }

  private val knowledgeRuntime: AppKnowledgeRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    AppKnowledgeRuntimeDependencies(
      database = databaseConnection,
      dataChanges = dataChanges,
      bookmarks = contentRuntime.bookmarkRepository,
      summaries = aiCoreRuntime.summaryRepository,
      modelManager = aiCoreRuntime.modelManager,
    )
  }

  private val crossFeatureRuntime: AppCrossFeatureRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    AppCrossFeatureRuntimeDependencies(
      application = application,
      database = database,
      modelManager = aiCoreRuntime.modelManager,
      articleRepository = contentRuntime.articleRepository,
      bookmarkContentQuery = contentRuntime.bookmarkContentQuery,
      bookmarkRepository = contentRuntime.bookmarkRepository,
      feedRepository = contentRuntime.feedRepository,
      redditRepository = contentRuntime.redditRepository,
      summaryRepository = aiCoreRuntime.summaryRepository,
      taskRepository = supportingRuntime.taskRepository,
      featureRuntimeDependencies = featureRuntimeDependencies,
    )
  }

  val bookmarkContentQuery get() = contentRuntime.bookmarkContentQuery
  val articleRepository get() = contentRuntime.articleRepository
  val assetRepository get() = supportingRuntime.assetRepository
  val bookmarkRepository get() = contentRuntime.bookmarkRepository
  val bookmarkEnrichmentRepository get() = contentRuntime.bookmarkEnrichmentRepository
  val bookmarkImportRepository get() = contentRuntime.bookmarkImportRepository
  val feedRepository get() = contentRuntime.feedRepository
  val redditRepository get() = contentRuntime.redditRepository
  val youtubeRepository get() = contentRuntime.youtubeRepository
  val feedImportRepository get() = contentRuntime.feedImportRepository
  val refreshFeedsUseCase get() = contentRuntime.refreshFeedsUseCase
  val backupChangeScheduler get() = contentRuntime.backupChangeScheduler
  val saveSharedBookmarkUseCase get() = contentRuntime.saveSharedBookmarkUseCase
  val widgetRepository get() = contentRuntime.widgetRepository
  val modelManager get() = aiCoreRuntime.modelManager
  val aiModelRepository get() = aiCoreRuntime.aiModelRepository
  val chatRepository get() = supportingRuntime.chatRepository
  val taskRepository get() = supportingRuntime.taskRepository
  val workoutRepository get() = supportingRuntime.workoutRepository
  val calendarRepository get() = supportingRuntime.calendarRepository
  val lanWebServerController get() = supportingRuntime.lanWebServerController
  val gmailAuthorizationManager get() = supportingRuntime.gmailAuthorizationManager
  val mailRepository get() = supportingRuntime.mailRepository
  val xViewerCssRepository get() = supportingRuntime.xViewerCssRepository
  val chatGenerator get() = crossFeatureRuntime.chatGenerator
  val backupRepository get() = supportingRuntime.backupRepository
  val summaryRepository get() = aiCoreRuntime.summaryRepository
  val backfillBookmarkAutoEnrichmentUseCase get() = crossFeatureRuntime.backfillBookmarkAutoEnrichmentUseCase
  val summaryTaskQueueRepository get() = crossFeatureRuntime.summaryTaskQueueRepository
  val knowledgeRepository get() = knowledgeRuntime.knowledgeRepository
  val knowledgeBuilder get() = knowledgeRuntime.knowledgeBuilder
  val knowledgePageCreator get() = knowledgeRuntime.knowledgePageCreator
  val knowledgePageEditor get() = knowledgeRuntime.knowledgePageEditor
  val aiTaskQueueRepository get() = crossFeatureRuntime.aiTaskQueueRepository
}
