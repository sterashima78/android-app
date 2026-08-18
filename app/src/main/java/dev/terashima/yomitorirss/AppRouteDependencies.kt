package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeViewModel
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.CleaningSmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.LocalLibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.data.SeriesAwareLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.task.TaskViewModel
import dev.terashima.yomitorirss.feature.widget.TaskWidgetUpdater
import dev.terashima.yomitorirss.feature.workout.WorkoutViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

class AppRouteDependencies internal constructor(
  application: Application,
  container: AppContainer,
) {
  val assetViewModelFactory: AssetViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AssetViewModel.Factory(
      repository = container.assetRepository,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
  }

  val knowledgeViewModelFactory: KnowledgeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val buildTaskController = WorkManagerKnowledgeBuildTaskController(application)
    KnowledgeViewModel.Factory(
      repository = container.knowledgeRepository,
      scheduleBackupAfterChange = container.backupChangeScheduler::scheduleAfterChange,
      scheduleRebuild = buildTaskController::enqueue,
    )
  }

  val library: LibraryRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val database = container.databaseConnection
    val smbRepository = CleaningSmbLibraryRepository(application, database)
    LibraryRouteDependencies(
      authorization = GoogleBooksAuthorizationManager(application),
      libraryViewModelFactory = LibraryViewModel.Factory(
        repository = SeriesAwareLibraryRepository(database),
        smbRepository = smbRepository,
      ),
      organizationViewModelFactory = LibraryOrganizationViewModel.Factory(
        repository = DefaultLibraryOrganizationRepository(database),
        suggester = LocalLibraryOrganizationSuggester(container.modelManager),
        batchScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
      ),
      smbRepository = smbRepository,
    )
  }

  val taskViewModelFactory: TaskViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    TaskViewModel.Factory(container.taskRepository)
  }

  val updateTaskWidget: () -> Unit = {
    TaskWidgetUpdater.updateAll(application)
  }

  val workoutViewModelFactory: WorkoutViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutViewModel.Factory(container.workoutRepository)
  }

  val youtubeViewModelFactory: YouTubeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YouTubeViewModel.Factory(
      repository = container.youtubeRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }
}

data class LibraryRouteDependencies internal constructor(
  val authorization: GoogleBooksAuthorizationManager,
  val libraryViewModelFactory: LibraryViewModel.Factory,
  val organizationViewModelFactory: LibraryOrganizationViewModel.Factory,
  val smbRepository: SmbLibraryRepository,
)
