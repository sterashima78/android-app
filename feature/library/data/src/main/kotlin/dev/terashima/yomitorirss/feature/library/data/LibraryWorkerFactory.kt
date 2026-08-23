package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler

/** Application-scope dependencies shared by Library WorkManager entry points. */
data class LibraryWorkerRuntimeDependencies(
  val database: DatabaseConnection,
  val smbRepository: SmbLibraryRepository,
  val smbCoverPrefetchScheduler: SmbCoverPrefetchScheduler,
  val smbMetadataNormalizationRepository: DefaultSmbMetadataNormalizationRepository,
  val smbMetadataNormalizationScheduler: SmbMetadataNormalizationScheduler,
  val smbMetadataNormalizationPromptRepository: SmbMetadataNormalizationPromptRepository,
  val organizationRepository: DefaultLibraryOrganizationRepository,
  val organizationLibraryRepository: LibraryRepository,
  val organizationSuggester: LibraryOrganizationSuggester,
  val organizationBatchScheduler: LibraryOrganizationBatchScheduler,
)

/**
 * Resolves Library Workers from the application graph while preserving their existing FQCNs.
 *
 * WorkManager persists Worker class names, so this factory changes dependency resolution without
 * renaming entry points that may already be queued on the current-version compatibility baseline.
 */
class LibraryWorkerFactory(
  private val runtimeProvider: () -> LibraryWorkerRuntimeDependencies,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? {
    if (workerClassName !in LIBRARY_WORKER_CLASS_NAMES) return null
    val runtime = runtimeProvider()
    return when (workerClassName) {
      SmbCoverPrefetchWorker::class.java.name -> SmbCoverPrefetchWorker(
        appContext = appContext,
        params = workerParameters,
        database = runtime.database,
      )
      SmbMetadataNormalizationResumeOnChargingWorker::class.java.name ->
        SmbMetadataNormalizationResumeOnChargingWorker(
          appContext = appContext,
          params = workerParameters,
          scheduler = runtime.smbMetadataNormalizationScheduler,
        )
      SmbMetadataNormalizationWorker::class.java.name -> SmbMetadataNormalizationWorker(
        appContext = appContext,
        params = workerParameters,
        database = runtime.database,
        smbRepository = runtime.smbRepository,
        repository = runtime.smbMetadataNormalizationRepository,
        scheduler = runtime.smbMetadataNormalizationScheduler,
        coverPrefetchScheduler = runtime.smbCoverPrefetchScheduler,
        promptRepository = runtime.smbMetadataNormalizationPromptRepository,
      )
      LibraryOrganizationResumeOnChargingWorker::class.java.name ->
        LibraryOrganizationResumeOnChargingWorker(
          appContext = appContext,
          params = workerParameters,
          repository = runtime.organizationRepository,
          scheduler = runtime.organizationBatchScheduler,
        )
      LibraryOrganizationBatchWorker::class.java.name -> LibraryOrganizationBatchWorker(
        appContext = appContext,
        params = workerParameters,
        organizationRepository = runtime.organizationRepository,
        libraryRepository = runtime.organizationLibraryRepository,
        suggester = runtime.organizationSuggester,
        scheduler = runtime.organizationBatchScheduler,
      )
      else -> null
    }
  }

  private companion object {
    val LIBRARY_WORKER_CLASS_NAMES = setOf(
      SmbCoverPrefetchWorker::class.java.name,
      SmbMetadataNormalizationResumeOnChargingWorker::class.java.name,
      SmbMetadataNormalizationWorker::class.java.name,
      LibraryOrganizationResumeOnChargingWorker::class.java.name,
      LibraryOrganizationBatchWorker::class.java.name,
    )
  }
}
