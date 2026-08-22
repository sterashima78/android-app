package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.health.data.HealthConnectHealthRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildScheduler
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.data.CleaningSmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultSmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.data.SmbMetadataAwareLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
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
    LibraryRuntimeDependencies(
      catalogRepository = SmbMetadataAwareLibraryRepository(database),
      organizationRepository = DefaultLibraryOrganizationRepository(database),
      organizationBatchScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
      smbRepository = smbRepository,
      smbMetadataNormalizationRepository = DefaultSmbMetadataNormalizationRepository(database, smbRepository),
      smbMetadataNormalizationScheduler = WorkManagerSmbMetadataNormalizationScheduler(application),
    )
  }
}

internal data class LibraryRuntimeDependencies(
  val catalogRepository: LibraryRepository,
  val organizationRepository: LibraryOrganizationRepository,
  val organizationBatchScheduler: LibraryOrganizationBatchScheduler,
  val smbRepository: SmbLibraryRepository,
  val smbMetadataNormalizationRepository: SmbMetadataNormalizationRepository,
  val smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler,
)
