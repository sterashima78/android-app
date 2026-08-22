package dev.terashima.yomitorirss.feature.task

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TaskRoute(
  viewModelFactory: TaskViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val taskViewModel: TaskViewModel = viewModel(factory = viewModelFactory)
  TaskScreen(viewModel = taskViewModel, modifier = modifier)
}
