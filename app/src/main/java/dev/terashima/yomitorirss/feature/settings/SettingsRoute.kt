package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.x.XViewerCssSettingsDialog

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
  var showXCssSettings by remember { mutableStateOf(false) }

  SettingsFeatureScreen(
    modifier = modifier,
    tagCount = tagCount,
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
    XViewerCssSettingsDialog(onDismiss = { showXCssSettings = false })
  }
}
