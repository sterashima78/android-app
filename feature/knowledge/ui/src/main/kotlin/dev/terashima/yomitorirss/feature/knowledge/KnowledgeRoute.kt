package dev.terashima.yomitorirss.feature.knowledge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun KnowledgeRoute(
  viewModelFactory: KnowledgeViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val knowledgeViewModel: KnowledgeViewModel = viewModel(factory = viewModelFactory)
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
    onStartDelete = knowledgeViewModel::startDelete,
    onCancelDelete = knowledgeViewModel::cancelDelete,
    onDeletePage = knowledgeViewModel::deletePage,
    onStartSplit = knowledgeViewModel::startSplit,
    onCancelSplit = knowledgeViewModel::cancelSplit,
    onSplitPage = knowledgeViewModel::splitPage,
    onStartMerge = knowledgeViewModel::startMerge,
    onCancelMerge = knowledgeViewModel::cancelMerge,
    onMergePage = knowledgeViewModel::mergePage,
  )
}
