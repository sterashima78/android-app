package dev.terashima.yomitorirss.feature.backup

sealed interface ConfigureGoogleDriveResult {
  data object Enabled : ConfigureGoogleDriveResult
  data class EnabledWithInitialBackupFailure(val message: String) : ConfigureGoogleDriveResult
}

interface BackupRepository {
  fun status(): GoogleDriveBackupStatus
  fun ensureScheduled()
  fun scheduleAfterChange()
  suspend fun exportTo(documentUri: String)
  suspend fun restoreFrom(documentUri: String)
  suspend fun configureGoogleDrive(folderUri: String): ConfigureGoogleDriveResult
  suspend fun backupToGoogleDriveNow(): String
  fun disableGoogleDrive()
}
