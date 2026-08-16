package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler

@Composable
fun TaskQueueScreen(onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val repository = remember(application) {
    CompositeAiTaskQueueRepository(
      summaryRepository = application.container.summaryTaskQueueRepository,
      libraryRepository = DefaultLibraryOrganizationRepository(
        DatabaseConnection(application.container.database),
      ),
      libraryScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
    )
  }
  AiTaskQueueScreen(
    repository = repository,
    onDismiss = onDismiss,
  )
}
