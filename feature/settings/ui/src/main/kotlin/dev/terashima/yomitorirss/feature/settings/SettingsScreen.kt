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
  feedCount: Int,
  tagCount: Int,
  onOpenFeeds: () -> Unit,
  onImportFeedOpml: () -> Unit,
  onImportBookmarkCsv: () -> Unit,
  onImportBookmarkHtml: () -> Unit,
  onOpenXCss: () -> Unit,
  onOpenModels: () -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  taskQueueContent: @Composable (onDismiss: () -> Unit) -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  var showTaskQueue by remember { mutableStateOf(false) }

  SettingsContent(
    modifier = modifier,
    feedCount = feedCount,
    tagCount = tagCount,
    onOpenFeeds = onOpenFeeds,
    onImportFeedOpml = onImportFeedOpml,
    onImportBookmarkCsv = onImportBookmarkCsv,
    onImportBookmarkHtml = onImportBookmarkHtml,
    onOpenXCss = onOpenXCss,
    onOpenModels = onOpenModels,
    onOpenSummaryPrompt = onOpenSummaryPrompt,
    onOpenSummaryTaskQueue = { showTaskQueue = true },
    onOpenDriveBackup = onOpenDriveBackup,
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  if (showTaskQueue) {
    taskQueueContent { showTaskQueue = false }
  }
}
