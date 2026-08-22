package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRoute

@Composable
fun SettingsScreen(
  modifier: Modifier,
  aiTaskQueueRepository: AiTaskQueueRepository,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  onOpenModels: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  var showAiTaskQueue by remember { mutableStateOf(false) }

  SettingsFeatureScreen(
    modifier = modifier,
    initialBackgroundFetchWifiOnly = initialBackgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = onBackgroundFetchWifiOnlyChange,
    onOpenModels = onOpenModels,
    onOpenSummaryPrompt = onOpenSummaryPrompt,
    onOpenAiTaskQueue = { showAiTaskQueue = true },
    onOpenDriveBackup = onOpenDriveBackup,
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  if (showAiTaskQueue) {
    AiTaskQueueRoute(
      repository = aiTaskQueueRepository,
      onDismiss = { showAiTaskQueue = false },
    )
  }
}
