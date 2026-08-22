package dev.terashima.yomitorirss.feature.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AssetRoute(
  viewModelFactory: AssetViewModel.Factory,
  modifier: Modifier,
) {
  val assetViewModel: AssetViewModel = viewModel(factory = viewModelFactory)
  AssetScreen(viewModel = assetViewModel, modifier = modifier)
}
