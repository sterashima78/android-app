package dev.terashima.yomitorirss.feature.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorkoutRoute(
  viewModelFactory: WorkoutViewModel.Factory,
  writePermissions: Set<String>,
  modifier: Modifier = Modifier,
) {
  val workoutViewModel: WorkoutViewModel = viewModel(factory = viewModelFactory)
  val permissionLauncher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract(),
  ) { grantedPermissions ->
    workoutViewModel.onExportPermissionResult(grantedPermissions.containsAll(writePermissions))
  }
  WorkoutScreen(
    viewModel = workoutViewModel,
    onRequestExportPermission = { permissionLauncher.launch(writePermissions) },
    modifier = modifier,
  )
}
