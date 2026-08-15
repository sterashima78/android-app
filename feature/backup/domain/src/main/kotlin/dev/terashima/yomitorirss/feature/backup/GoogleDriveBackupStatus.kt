package dev.terashima.yomitorirss.feature.backup

data class GoogleDriveBackupStatus(
  val folderUri: String? = null,
  val folderName: String? = null,
  val lastSuccessAt: String? = null,
  val lastFileName: String? = null,
  val lastError: String? = null,
) {
  val configured: Boolean get() = folderUri != null
}
