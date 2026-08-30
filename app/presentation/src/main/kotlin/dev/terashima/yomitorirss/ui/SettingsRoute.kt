package dev.terashima.yomitorirss.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
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
  initialIntegratedRefreshIntervalMinutes: Long,
  onIntegratedRefreshIntervalChange: (Long) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenWebServer: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  val context = LocalContext.current
  val backupState by backupViewModel.state.collectAsState()
  val notificationPermissionResultBridge = remember { NotificationPermissionResultBridge() }
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> notificationPermissionResultBridge.complete(granted) }
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
      onNavigate(INTEGRATED_ROUTE)
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
    initialIntegratedRefreshIntervalMinutes = initialIntegratedRefreshIntervalMinutes,
    onIntegratedRefreshIntervalChange = onIntegratedRefreshIntervalChange,
    initialNotificationPermissionGranted =
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    onRequestNotificationPermission = { onResult ->
      notificationPermissionResultBridge.prepare(onResult)
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    },
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

private class NotificationPermissionResultBridge {
  private var onResult: ((Boolean) -> Unit)? = null

  fun prepare(callback: (Boolean) -> Unit) {
    onResult = callback
  }

  fun complete(granted: Boolean) {
    onResult?.invoke(granted)
    onResult = null
  }
}
