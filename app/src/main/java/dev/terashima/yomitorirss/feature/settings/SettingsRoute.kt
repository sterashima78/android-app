package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
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
  val backgroundDataFetchPreferences = remember(context) {
    BackgroundDataFetchPreferences(context)
  }
  var backgroundFetchWifiOnly by remember {
    mutableStateOf(backgroundDataFetchPreferences.wifiOnly)
  }
  var showXCssSettings by remember { mutableStateOf(false) }

  SettingsFeatureScreen(
    modifier = modifier,
    tagCount = tagCount,
    backgroundFetchWifiOnly = backgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = { wifiOnly ->
      backgroundDataFetchPreferences.wifiOnly = wifiOnly
      backgroundFetchWifiOnly = wifiOnly
      MailSyncScheduler(context).refreshPeriodicNetworkPolicy()
    },
    onImportBookmarkCsv = onImportBookmarkCsv,
    onImportBookmarkHtml = onImportBookmarkHtml,
    onOpenXCss = { showXCssSettings = true },
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
}
