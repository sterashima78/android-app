package dev.terashima.yomitorirss.composition.library

import android.app.Activity
import android.app.Application
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import dev.terashima.yomitorirss.feature.bookreader.data.DefaultBookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.data.SharedPreferencesReadingPositionStore
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorRepository
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorTester
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import dev.terashima.yomitorirss.feature.library.data.AndroidWebViewLibraryMetadataClient
import dev.terashima.yomitorirss.feature.library.data.AndroidWebViewLibraryMetadataExtractorTester
import dev.terashima.yomitorirss.feature.library.data.CleaningSmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.data.DefaultSmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultWebLibraryMetadataExtractorRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultWebLibraryMutator
import dev.terashima.yomitorirss.feature.library.data.ForegroundWebLibraryRenderedMetadataClient
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.LibraryWorkerRuntimeDependencies
import dev.terashima.yomitorirss.feature.library.data.SharedPreferencesSmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.data.SmbMetadataAwareLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WebLibraryMetadataClient
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.data.WorkManagerSmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.data.WorkManagerSmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizationDependencies
import dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizationOutcome
import dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizedAccount

/** Library repositories, schedulers, authorization bridge, and reader dependencies at application scope. */
internal class AppLibraryRuntimeDependencies(
  application: Application,
  database: DatabaseConnection,
  httpClient: HttpClient,
  textInferenceProvider: () -> AiTextInference,
  structuredTextInferenceProvider: () -> AiStructuredTextInference,
  resumedActivityProvider: () -> Activity?,
) {
  val runtime: LibraryRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val smbRepository = CleaningSmbLibraryRepository(application, database)
    val catalogRepository = SmbMetadataAwareLibraryRepository(database)
    val organizationRepository = DefaultLibraryOrganizationRepository(database)
    val organizationSuggester = DefaultLibraryOrganizationSuggester(
      textInference = textInferenceProvider(),
      structuredInference = structuredTextInferenceProvider(),
    )
    val organizationBatchScheduler = WorkManagerLibraryOrganizationBatchScheduler(application)
    val smbCoverPrefetchScheduler = WorkManagerSmbCoverPrefetchScheduler(application)
    val smbMetadataNormalizationRepository = DefaultSmbMetadataNormalizationRepository(database, smbRepository)
    val smbMetadataNormalizationScheduler = WorkManagerSmbMetadataNormalizationScheduler(application)
    val smbMetadataNormalizationPromptRepository =
      SharedPreferencesSmbMetadataNormalizationPromptRepository(application)
    val webMetadataExtractorRepository = DefaultWebLibraryMetadataExtractorRepository(database)
    val webMetadataExtractorTester = AndroidWebViewLibraryMetadataExtractorTester(resumedActivityProvider)
    val authorizationManager = GoogleBooksAuthorizationManager(application)
    val renderedMetadataClient = AndroidWebViewLibraryMetadataClient(
      activityProvider = resumedActivityProvider,
      extractorRepository = webMetadataExtractorRepository,
    )

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
      catalogRepository = catalogRepository,
      webLibraryMutator = DefaultWebLibraryMutator(
        database = database,
        metadataClient = WebLibraryMetadataClient(httpClient),
        renderedMetadataClient = ForegroundWebLibraryRenderedMetadataClient(
          delegate = renderedMetadataClient,
          activityProvider = resumedActivityProvider,
        ),
      ),
      webMetadataExtractorRepository = webMetadataExtractorRepository,
      webMetadataExtractorTester = webMetadataExtractorTester,
      organizationRepository = organizationRepository,
      organizationSuggester = organizationSuggester,
      organizationBatchScheduler = organizationBatchScheduler,
      smbRepository = smbRepository,
      smbCoverPrefetchScheduler = smbCoverPrefetchScheduler,
      smbMetadataNormalizationRepository = smbMetadataNormalizationRepository,
      smbMetadataNormalizationScheduler = smbMetadataNormalizationScheduler,
      smbMetadataNormalizationPromptRepository = smbMetadataNormalizationPromptRepository,
      bookPageSourceFactory = DefaultBookPageSourceFactory(),
      readingPositionStore = SharedPreferencesReadingPositionStore(application),
      workerRuntime = LibraryWorkerRuntimeDependencies(
        database = database,
        smbRepository = smbRepository,
        smbCoverPrefetchScheduler = smbCoverPrefetchScheduler,
        smbMetadataNormalizationRepository = smbMetadataNormalizationRepository,
        smbMetadataNormalizationScheduler = smbMetadataNormalizationScheduler,
        smbMetadataNormalizationPromptRepository = smbMetadataNormalizationPromptRepository,
        organizationRepository = organizationRepository,
        organizationLibraryRepository = catalogRepository,
        organizationSuggester = organizationSuggester,
        organizationBatchScheduler = organizationBatchScheduler,
      ),
    )
  }
}

internal data class LibraryRuntimeDependencies(
  val authorization: LibraryAuthorizationDependencies,
  val catalogRepository: LibraryRepository,
  val webLibraryMutator: WebLibraryMutator,
  val webMetadataExtractorRepository: WebLibraryMetadataExtractorRepository,
  val webMetadataExtractorTester: WebLibraryMetadataExtractorTester,
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
  val workerRuntime: LibraryWorkerRuntimeDependencies,
)
