package dev.terashima.yomitorirss

import android.app.Activity
import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
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
  private val persistenceChanges = PersistenceChangeNotifier.shared

  internal val httpClient: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HttpClient.create(userAgent = "Mosaic/${BuildConfig.VERSION_NAME} (Android)")
  }

  val database: YomitoriDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YomitoriDatabase.create(application)
  }

  internal val databaseConnection: DatabaseConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DatabaseConnection(database, persistenceChanges)
  }

  private val featureRuntimeDependencies: AppFeatureRuntimeDependencies by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    AppFeatureRuntimeDependencies(
      application = application,
      database = databaseConnection,
      httpClient = httpClient,
      textInferenceProvider = { aiCoreRuntime.textInference },
      resumedActivityProvider = resumedActivityProvider,
    )
  }

  private val aiCoreRuntime: AppAiCoreRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AppAiCoreRuntimeDependencies(application, database, httpClient)
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
      persistenceChanges = persistenceChanges,
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
      localTextInference = aiCoreRuntime.textInference,
      cloudTextInference = aiCoreRuntime.knowledgeCloudTextInference,
      executionSettings = featureRuntimeDependencies.knowledgeExecutionSettings,
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
      libraryRuntime = libraryRuntime,
      knowledgeBuildTaskController = featureRuntimeDependencies.knowledgeBuildTaskController,
      knowledgeExecutionSettings = featureRuntimeDependencies.knowledgeExecutionSettings,
    )
  }

  internal val healthRepository get() = featureRuntimeDependencies.healthRepository
  internal val libraryRuntime get() = featureRuntimeDependencies.library
  internal val libraryWorkerRuntime get() = libraryRuntime.workerRuntime
  internal val knowledgeBuildScheduler get() = featureRuntimeDependencies.knowledgeBuildScheduler
  internal val knowledgeBuildRunner get() = knowledgeRuntime.knowledgeBuildRunner
  internal val knowledgeExecutionSettings get() = featureRuntimeDependencies.knowledgeExecutionSettings
  internal val textInference get() = aiCoreRuntime.textInference
  internal val summaryRepository get() = aiCoreRuntime.summaryRepository
  internal val aiModelManager get() = aiCoreRuntime.modelManager
  internal val localAiExecutionSettings get() = aiCoreRuntime.localAiExecutionSettings
  internal val localAiSummaryRunner get() = aiCoreRuntime.localAiSummaryRunner
  internal val summaryQueueRepository get() = aiCoreRuntime.summaryQueueRepository
  internal val assetRepository get() = supportingRuntime.assetRepository
  internal val chatRepository get() = supportingRuntime.chatRepository
  internal val taskRepository get() = supportingRuntime.taskRepository
  internal val workoutRepository get() = supportingRuntime.workoutRepository
  internal val workoutHistoryExporter get() = supportingRuntime.workoutHistoryExporter
  internal val calendarRepository get() = supportingRuntime.calendarRepository
  internal val lanWebServerController get() = supportingRuntime.lanWebServerController
  internal val mailAuthorization get() = supportingRuntime.mailAuthorization
  internal val mailRepository get() = supportingRuntime.mailRepository
  internal val backupRepository get() = supportingRuntime.backupRepository
  internal val xViewerCssRepository get() = supportingRuntime.xViewerCssRepository
  internal val youtubeRepository get() = contentRuntime.youtubeRepository
  internal val feedImportRepository get() = contentRuntime.feedImportRepository
  internal val refreshFeedsUseCase get() = contentRuntime.refreshFeedsUseCase
  internal val saveSharedBookmarkUseCase get() = contentRuntime.saveSharedBookmarkUseCase
  internal val widgetRepository get() = contentRuntime.widgetRepository
  internal val widgetRefreshScheduler get() = supportingRuntime.widgetRefreshScheduler
  internal val bookmarkRepository get() = contentRuntime.bookmarkRepository
  internal val articleRepository get() = contentRuntime.articleRepository
  internal val feedRepository get() = contentRuntime.feedRepository
  internal val redditRepository get() = contentRuntime.redditRepository
  internal val bookmarkImportRepository get() = contentRuntime.bookmarkImportRepository
  internal val knowledgeRepository get() = knowledgeRuntime.knowledgeRepository
  internal val knowledgeBuilder get() = knowledgeRuntime.knowledgeBuilder
  internal val knowledgePageCreator get() = knowledgeRuntime.knowledgePageCreator
  internal val knowledgePageEditor get() = knowledgeRuntime.knowledgePageEditor
}
