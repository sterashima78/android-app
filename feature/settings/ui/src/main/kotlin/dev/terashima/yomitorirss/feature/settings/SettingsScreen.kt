package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun SettingsFeatureScreen(
  modifier: Modifier,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  onOpenModels: () -> Unit,
  onOpenChatGptDebug: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenAiTaskQueue: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  var backgroundFetchWifiOnly by remember(initialBackgroundFetchWifiOnly) {
    mutableStateOf(initialBackgroundFetchWifiOnly)
  }

  SettingsContent(
    modifier = modifier,
    backgroundFetchWifiOnly = backgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = { wifiOnly ->
      backgroundFetchWifiOnly = wifiOnly
      onBackgroundFetchWifiOnlyChange(wifiOnly)
    },
    onOpenModels = onOpenModels,
    onOpenChatGptDebug = onOpenChatGptDebug,
    onOpenSummaryPrompt = onOpenSummaryPrompt,
    onOpenAiTaskQueue = onOpenAiTaskQueue,
    onOpenDriveBackup = onOpenDriveBackup,
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )
}
