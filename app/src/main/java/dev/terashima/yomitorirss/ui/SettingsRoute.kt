package dev.terashima.yomitoririss.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.settings.SettingsFeatureScreen
import java.time.LocalDate

@Composable
internal fun SettingsRoute(
  modifier: Modifier,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  aiTaskQueueRepository: AiTaskQueueRepository,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenWebServer: () -> Unit,
  onNavigate: (MainTab) -> Unit,
) {
  val backupState by backupViewModel.state.collectAsState()

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri -> uri?.toString()?.let(backupViewModel::exportBackup) }
  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(backupViewModel::importBackup) }
  val backupFolderLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri -> uri?.toString()?.let(backupViewModel::configureGoogleDrive) }

  LaunchedEffect(backupState.restoreCompleted) {
    if (backupState.restoreCompleted) {
      onNavigate(MainTab.INTEGRATED)
      backupViewModel.consumeRestoreCompleted()
    }
  }

  SettingsFeatureScreen(
    modifier = modifier,
    backupViewModel = backupViewModel,
    aiSettingsViewModel = aiSettingsViewModel,
    aiTaskQueueRepository = aiTaskQueueRepository,
    initialBackgroundFetchWifiOnly = initialBackgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = onBackgroundFetchWifiOnlyChange,
    biometricLockEnabled = biometricLockEnabled,
    onBiometricLockEnabledChange = onBiometricLockEnabledChange,
    onSelectBackupFolder = { folderUri ->
      val initialUri = folderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
      backupFolderLauncher.launch(initialUri)
    },
    onExportBackup = { exportLauncher.launch("mosaic-backup-${LocalDate.now()}.zip") },
    onImportBackup = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
    onOpenWebServer = onOpenWebServer,
  )
}
