package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.feature.asset.AssetManagementDialog
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.mail.data.MailSyncScheduler
import dev.terashima.yomitorirss.feature.x.XViewerCssSettingsSheet

@Composable
fun SettingsScreen(
  modifier: Modifier,
  tagCount: Int,
  onImportBookmarkCsv: () -> Unit,
  onImportBookmarkHtml: () -> Unit,
  onOpenModels: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val backgroundDataFetchPreferences = remember(context) {
    BackgroundDataFetchPreferences(context)
  }
  val initialBackgroundFetchWifiOnly = remember(backgroundDataFetchPreferences) {
    backgroundDataFetchPreferences.wifiOnly
  }
  var showXCssSettings by remember { mutableStateOf(false) }
  var showAssetManagement by remember { mutableStateOf(false) }
  val assetViewModel: AssetViewModel = viewModel(
    factory = AssetViewModel.Factory(
      repository = application.container.assetRepository,
      onChanged = application.container.backupChangeScheduler::scheduleAfterChange,
    ),
  )

  SettingsFeatureScreen(
    modifier = modifier,
    tagCount = tagCount,
    initialBackgroundFetchWifiOnly = initialBackgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = { wifiOnly ->
      backgroundDataFetchPreferences.wifiOnly = wifiOnly
      MailSyncScheduler(context).refreshPeriodicNetworkPolicy()
    },
    onImportBookmarkCsv = onImportBookmarkCsv,
    onImportBookmarkHtml = onImportBookmarkHtml,
    onOpenXCss = { showXCssSettings = true },
    onOpenAssetManagement = { showAssetManagement = true },
    onOpenModels = onOpenModels,
    onOpenSummaryPrompt = onOpenSummaryPrompt,
    taskQueueContent = { onDismiss -> TaskQueueScreen(onDismiss) },
    onOpenDriveBackup = onOpenDriveBackup,
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  if (showXCssSettings) {
    XViewerCssSettingsSheet(onDismiss = { showXCssSettings = false })
  }
  if (showAssetManagement) {
    AssetManagementDialog(
      viewModel = assetViewModel,
      onDismiss = { showAssetManagement = false },
    )
  }
}
