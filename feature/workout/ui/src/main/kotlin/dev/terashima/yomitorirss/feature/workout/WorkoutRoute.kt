package dev.terashima.yomitorirss.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorkoutRoute(
  viewModelFactory: WorkoutViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val workoutViewModel: WorkoutViewModel = viewModel(factory = viewModelFactory)
  WorkoutScreen(viewModel = workoutViewModel, modifier = modifier)
}
