package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.ConfigureGoogleDriveResult
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupStatus
import dev.terashima.yomitorirss.feature.bookmark.data.BookmarkDatabaseInitializer
import org.json.JSONObject

class DefaultBackupRepository(
  context: Context,
  private val database: YomitoriDatabase,
  private val dataChanges: DataChangeNotifier,
) : BackupRepository {
  private val appContext = context.applicationContext
  private val preferences = GoogleDriveBackupPreferences(appContext)
  private val service = GoogleDriveBackupService(appContext)

  override fun status(): GoogleDriveBackupStatus = preferences.status()

  override fun ensureScheduled() {
    GoogleDriveBackupScheduler.ensureScheduled(appContext)
  }

  override fun scheduleAfterChange() {
    GoogleDriveBackupScheduler.scheduleAfterChange(appContext)
  }

  override suspend fun exportTo(documentUri: String) {
    val uri = Uri.parse(documentUri)
    val json = database.exportBackup().toString(2)
    appContext.contentResolver.openOutputStream(uri, "w")
      ?.bufferedWriter(Charsets.UTF_8)
      ?.use { it.write(json) }
      ?: error("保存先を開けませんでした")
  }

  override suspend fun restoreFrom(documentUri: String) {
    val uri = Uri.parse(documentUri)
    val text = appContext.contentResolver.openInputStream(uri)
      ?.bufferedReader(Charsets.UTF_8)
      ?.use { it.readText() }
      ?: error("バックアップを開けませんでした")
    database.restoreBackup(JSONObject(text))
    BookmarkDatabaseInitializer.initialize(DatabaseConnection(database))
    dataChanges.notifyChanged()
    scheduleAfterChange()
  }

  override suspend fun configureGoogleDrive(folderUri: String): ConfigureGoogleDriveResult {
    val treeUri = Uri.parse(folderUri)
    val previousUri = preferences.status().folderUri?.let(Uri::parse)
    appContext.contentResolver.takePersistableUriPermission(
      treeUri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    preferences.configure(treeUri, folderDisplayName(treeUri))
    GoogleDriveBackupScheduler.schedulePeriodic(appContext)
    if (previousUri != null && previousUri != treeUri) {
      runCatching {
        appContext.contentResolver.releasePersistableUriPermission(
          previousUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }

    return runCatching { service.backup() }
      .fold(
        onSuccess = { ConfigureGoogleDriveResult.Enabled },
        onFailure = { ConfigureGoogleDriveResult.EnabledWithInitialBackupFailure(it.userMessage()) },
      )
  }

  override suspend fun backupToGoogleDriveNow(): String {
    check(preferences.isConfigured()) { "Google Driveの保存先を設定してください" }
    return service.backup()
  }

  override fun disableGoogleDrive() {
    preferences.status().folderUri?.let(Uri::parse)?.let { folderUri ->
      runCatching {
        appContext.contentResolver.releasePersistableUriPermission(
          folderUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
    }
    preferences.clearConfiguration()
    GoogleDriveBackupScheduler.cancel(appContext)
  }

  private fun folderDisplayName(treeUri: Uri): String {
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
      treeUri,
      DocumentsContract.getTreeDocumentId(treeUri),
    )
    return appContext.contentResolver.query(
      documentUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: "Google Drive"
  }
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
