package dev.terashima.yomitorirss.feature.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CalendarRoute(
  viewModelFactory: CalendarViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val calendarViewModel: CalendarViewModel = viewModel(factory = viewModelFactory)
  var permissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
      ) == PackageManager.PERMISSION_GRANTED,
    )
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    permissionGranted = granted
    calendarViewModel.reload()
  }

  CalendarScreen(
    viewModel = calendarViewModel,
    calendarPermissionGranted = permissionGranted,
    onRequestCalendarPermission = {
      permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    },
    modifier = modifier,
  )
}
