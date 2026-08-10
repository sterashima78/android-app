package dev.terashima.yomitorirss.feature.task

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun TaskScreen(modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val taskViewModel: TaskViewModel = viewModel(
    factory = TaskViewModel.Factory(application.container.taskRepository),
  )
  TaskScreen(viewModel = taskViewModel, modifier = modifier)
}
