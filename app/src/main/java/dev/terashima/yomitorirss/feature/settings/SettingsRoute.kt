package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication
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
  val summaryRepository = remember(context) {
    (context.applicationContext as YomitoriApplication).container.summaryRepository
  }
  var showXCssSettings by remember { mutableStateOf(false) }
  var autoSummarizeReadLater by remember {
    mutableStateOf(summaryRepository.isAutoSummarizeReadLaterEnabled())
  }

  SettingsFeatureScreen(
    modifier = modifier,
    tagCount = tagCount,
    autoSummarizeReadLater = autoSummarizeReadLater,
    onAutoSummarizeReadLaterChange = { enabled ->
      summaryRepository.setAutoSummarizeReadLaterEnabled(enabled)
      autoSummarizeReadLater = enabled
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
