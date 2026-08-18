package dev.terashima.yomitorirss.feature.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TaskScreen(
  viewModelFactory: TaskViewModel.Factory,
  onTasksChanged: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val taskViewModel: TaskViewModel = viewModel(factory = viewModelFactory)
  val state by taskViewModel.state.collectAsState()

  LaunchedEffect(state.tasks) {
    onTasksChanged()
  }

  TaskScreen(viewModel = taskViewModel, modifier = modifier)
}
