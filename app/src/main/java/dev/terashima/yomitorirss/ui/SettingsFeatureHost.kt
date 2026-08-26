package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupDialog
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.settings.SettingsFeatureScreen

private enum class SettingsCrossFeatureOverlay {
  SUMMARY_PROMPT,
  AI_TASK_QUEUE,
  DRIVE_BACKUP,
}

@Composable
internal fun SettingsFeatureHost(
  modifier: Modifier,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  aiTaskQueueRepository: AiTaskQueueRepository,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onSelectBackupFolder: (String?) -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  val backupState by backupViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()
  var crossFeatureOverlay by remember { mutableStateOf<SettingsCrossFeatureOverlay?>(null) }

  SettingsFeatureScreen(
    modifier = modifier,
    aiSettingsViewModel = aiSettingsViewModel,
    initialBackgroundFetchWifiOnly = initialBackgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = onBackgroundFetchWifiOnlyChange,
    biometricLockEnabled = biometricLockEnabled,
    onBiometricLockEnabledChange = onBiometricLockEnabledChange,
    onOpenSummaryPrompt = { crossFeatureOverlay = SettingsCrossFeatureOverlay.SUMMARY_PROMPT },
    onOpenAiTaskQueue = { crossFeatureOverlay = SettingsCrossFeatureOverlay.AI_TASK_QUEUE },
    onOpenDriveBackup = {
      backupViewModel.refreshStatus()
      crossFeatureOverlay = SettingsCrossFeatureOverlay.DRIVE_BACKUP
    },
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  when (crossFeatureOverlay) {
    SettingsCrossFeatureOverlay.SUMMARY_PROMPT -> SummaryPromptDialog(
      prompt = aiState.summaryPrompt,
      onDismiss = { crossFeatureOverlay = null },
      onSave = {
        crossFeatureOverlay = null
        aiSettingsViewModel.updateSummaryPrompt(it)
      },
      onReset = {
        crossFeatureOverlay = null
        aiSettingsViewModel.resetSummaryPrompt()
      },
    )

    SettingsCrossFeatureOverlay.AI_TASK_QUEUE -> AiTaskQueueRoute(
      repository = aiTaskQueueRepository,
      onDismiss = { crossFeatureOverlay = null },
    )

    SettingsCrossFeatureOverlay.DRIVE_BACKUP -> GoogleDriveBackupDialog(
      state = backupState,
      onDismiss = { crossFeatureOverlay = null },
      onSelectFolder = { onSelectBackupFolder(backupState.folderUri) },
      onBackupNow = backupViewModel::backupToGoogleDriveNow,
      onDisable = backupViewModel::disableGoogleDrive,
    )

    null -> Unit
  }
}
