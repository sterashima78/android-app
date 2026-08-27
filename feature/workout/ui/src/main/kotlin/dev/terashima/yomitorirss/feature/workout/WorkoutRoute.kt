package dev.terashima.yomitorirss.feature.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorkoutRoute(
  viewModelFactory: WorkoutViewModel.Factory,
  aiViewModelFactory: WorkoutAiViewModel.Factory,
  writePermissions: Set<String>,
  modifier: Modifier = Modifier,
) {
  val workoutViewModel: WorkoutViewModel = viewModel(factory = viewModelFactory)
  val workoutAiViewModel: WorkoutAiViewModel = viewModel(factory = aiViewModelFactory)
  val permissionLauncher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract(),
  ) { grantedPermissions ->
    workoutViewModel.onExportPermissionResult(grantedPermissions.containsAll(writePermissions))
  }
  Column(modifier.fillMaxSize()) {
    WorkoutAiPanel(viewModel = workoutAiViewModel)
    WorkoutScreen(
      viewModel = workoutViewModel,
      onRequestExportPermission = { permissionLauncher.launch(writePermissions) },
      modifier = Modifier.weight(1f),
    )
  }
}
