package dev.terashima.yomitorirss.feature.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.feature.widget.TaskWidgetUpdater

@Composable
fun TaskScreen(modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val taskViewModel: TaskViewModel = viewModel(
    factory = TaskViewModel.Factory(application.container.taskRepository),
  )
  val state by taskViewModel.state.collectAsState()

  LaunchedEffect(state.tasks) {
    TaskWidgetUpdater.updateAll(application)
  }

  TaskScreen(viewModel = taskViewModel, modifier = modifier)
}
