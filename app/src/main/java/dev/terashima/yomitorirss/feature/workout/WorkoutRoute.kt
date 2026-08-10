package dev.terashima.yomitorirss.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun WorkoutRoute(modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val workoutViewModel: WorkoutViewModel = viewModel(
    factory = WorkoutViewModel.Factory(application.container.workoutRepository),
  )
  WorkoutScreen(viewModel = workoutViewModel, modifier = modifier)
}
