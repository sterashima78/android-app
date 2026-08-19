package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRoute
import dev.terashima.yomitorirss.feature.mail.data.MailSyncScheduler

@Composable
fun SettingsScreen(
  modifier: Modifier,
  tagCount: Int,
  aiTaskQueueRepository: AiTaskQueueRepository,
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
  val initialBackgroundFetchWifiOnly = remember(backgroundDataFetchPreferences) {
    backgroundDataFetchPreferences.wifiOnly
  }
  var showAiTaskQueue by remember { mutableStateOf(false) }

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
