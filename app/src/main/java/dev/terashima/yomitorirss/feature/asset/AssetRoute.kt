package dev.terashima.yomitorirss.feature.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun AssetRoute(modifier: Modifier) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val assetViewModel: AssetViewModel = viewModel(
    factory = AssetViewModel.Factory(
      repository = application.container.assetRepository,
      onChanged = application.container.backupChangeScheduler::scheduleAfterChange,
    ),
  )

  AssetScreen(
    viewModel = assetViewModel,
    modifier = modifier,
  )
}
