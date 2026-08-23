package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import dev.terashima.yomitorirss.feature.bookreader.data.DefaultBookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.data.SharedPreferencesReadingPositionStore
import dev.terashima.yomitorirss.feature.health.data.HealthConnectHealthRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildScheduler
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import dev.terashima.yomitorirss.feature.library.data.CleaningSmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultSmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultWebLibraryMutator
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.LocalLibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.data.SharedPreferencesSmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.data.SmbMetadataAwareLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.data.WorkManagerSmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.data.WorkManagerSmbMetadataNormalizationScheduler

/**
 * Application-scope feature runtimes consumed by more than one composition adapter.
 *
 * AppContainer owns these instances so routes and cross-feature adapters do not construct
 * parallel repository/scheduler graphs for the same durable feature state.
 */
internal class AppFeatureRuntimeDependencies(
  application: Application,
  database: DatabaseConnection,
  modelManager: LocalModelManager,
) {
  val healthRepository: HealthConnectHealthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HealthConnectHealthRepository(application)
  }

  private val knowledgeBuildRuntime: WorkManagerKnowledgeBuildTaskController by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    WorkManagerKnowledgeBuildTaskController(application)
  }

  val knowledgeBuildTaskController: KnowledgeBuildTaskController
    get() = knowledgeBuildRuntime

  val knowledgeBuildScheduler: KnowledgeBuildScheduler = KnowledgeBuildScheduler {
    knowledgeBuildRuntime.enqueue()
  }

  val library: LibraryRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val smbRepository = CleaningSmbLibraryRepository(application, database)
    val authorizationManager = GoogleBooksAuthorizationManager(application)
    LibraryRuntimeDependencies(
      authorization = LibraryAuthorizationDependencies(
        requestAccount = {
          when (val outcome = authorizationManager.requestAccount()) {
            is GoogleBooksAuthorizationOutcome.Authorized -> LibraryAuthorizationOutcome.Authorized(
              LibraryAuthorizedAccount(
                accessToken = outcome.account.accessToken,
                accountLabel = outcome.account.accountLabel,
              ),
            )
            is GoogleBooksAuthorizationOutcome.RequiresResolution ->
              LibraryAuthorizationOutcome.RequiresResolution(outcome.pendingIntent)
          }
        },
        resultFromIntent = { data ->
          authorizationManager.resultFromIntent(data).let { account ->
            LibraryAuthorizedAccount(
              accessToken = account.accessToken,
              accountLabel = account.accountLabel,
            )
          }
        },
      ),
      catalogRepository = SmbMetadataAwareLibraryRepository(database),
      webLibraryMutator = DefaultWebLibraryMutator(database),
      organizationRepository = DefaultLibraryOrganizationRepository(database),
      organizationSuggester = LocalLibraryOrganizationSuggester(modelManager),
      organizationBatchScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
      smbRepository = smbRepository,
      smbCoverPrefetchScheduler = WorkManagerSmbCoverPrefetchScheduler(application),
      smbMetadataNormalizationRepository = DefaultSmbMetadataNormalizationRepository(database, smbRepository),
      smbMetadataNormalizationScheduler = WorkManagerSmbMetadataNormalizationScheduler(application),
      smbMetadataNormalizationPromptRepository =
        SharedPreferencesSmbMetadataNormalizationPromptRepository(application),
      bookPageSourceFactory = DefaultBookPageSourceFactory(),
      readingPositionStore = SharedPreferencesReadingPositionStore(application),
    )
  }
}

internal data class LibraryRuntimeDependencies(
  val authorization: LibraryAuthorizationDependencies,
  val catalogRepository: LibraryRepository,
  val webLibraryMutator: WebLibraryMutator,
  val organizationRepository: LibraryOrganizationRepository,
  val organizationSuggester: LibraryOrganizationSuggester,
  val organizationBatchScheduler: LibraryOrganizationBatchScheduler,
  val smbRepository: SmbLibraryRepository,
  val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler,
  val smbMetadataNormalizationRepository: SmbMetadataNormalizationRepository,
  val smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler,
  val smbMetadataNormalizationPromptRepository: SmbMetadataNormalizationPromptRepository,
  val bookPageSourceFactory: BookPageSourceFactory,
  val readingPositionStore: ReadingPositionStore,
)
