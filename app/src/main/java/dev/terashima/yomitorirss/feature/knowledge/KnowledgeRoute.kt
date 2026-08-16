package dev.terashima.yomitorirss.feature.knowledge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun KnowledgeRoute(modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val buildTaskController = remember(application) {
    WorkManagerKnowledgeBuildTaskController(application)
  }
  val knowledgeViewModel: KnowledgeViewModel = viewModel(
    factory = KnowledgeViewModel.Factory(
      repository = application.container.knowledgeRepository,
      scheduleBackupAfterChange = application.container.backupChangeScheduler::scheduleAfterChange,
      scheduleRebuild = buildTaskController::enqueue,
    ),
  )
  val state by knowledgeViewModel.state.collectAsState()

  KnowledgeScreen(
    modifier = modifier,
    state = state,
    onQueryChange = knowledgeViewModel::updateQuery,
    onOpenPage = knowledgeViewModel::openPage,
    onClosePage = knowledgeViewModel::closePage,
    onRebuild = knowledgeViewModel::rebuild,
    onStartCreate = { knowledgeViewModel.startCreate() },
    onStartRelatedCreate = knowledgeViewModel::startCreate,
    onComposerRequestChange = knowledgeViewModel::updateComposerRequest,
    onCreatePage = knowledgeViewModel::createPage,
    onCancelCreate = knowledgeViewModel::cancelCreate,
    onEditInstructionChange = knowledgeViewModel::updateEditInstruction,
    onEditPage = knowledgeViewModel::editPage,
  )
}
