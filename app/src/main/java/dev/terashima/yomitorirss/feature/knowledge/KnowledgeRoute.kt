package dev.terashima.yomitorirss.feature.knowledge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun KnowledgeRoute(modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val knowledgeViewModel: KnowledgeViewModel = viewModel(
    factory = KnowledgeViewModel.Factory(application.container.knowledgeRepository),
  )
  val state by knowledgeViewModel.state.collectAsState()

  KnowledgeScreen(
    modifier = modifier,
    state = state,
    onQueryChange = knowledgeViewModel::updateQuery,
    onOpenPage = knowledgeViewModel::openPage,
    onClosePage = knowledgeViewModel::closePage,
    onRebuild = knowledgeViewModel::rebuild,
  )
}
