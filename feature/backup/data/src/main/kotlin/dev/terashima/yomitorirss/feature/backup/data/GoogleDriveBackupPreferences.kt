package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupStatus
import java.time.Instant

class GoogleDriveBackupPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

  fun status(): GoogleDriveBackupStatus = GoogleDriveBackupStatus(
    folderUri = preferences.getString(KEY_FOLDER_URI, null),
    folderName = preferences.getString(KEY_FOLDER_NAME, null),
    lastSuccessAt = preferences.getString(KEY_LAST_SUCCESS_AT, null),
    lastFileName = preferences.getString(KEY_LAST_FILE_NAME, null),
    lastError = preferences.getString(KEY_LAST_ERROR, null),
    wifiOnly = isWifiOnly(),
  )

  fun isConfigured(): Boolean = preferences.contains(KEY_FOLDER_URI)

  fun isWifiOnly(): Boolean = preferences.getBoolean(KEY_WIFI_ONLY, false)

  fun configure(folderUri: Uri, folderName: String) {
    preferences.edit()
      .putString(KEY_FOLDER_URI, folderUri.toString())
      .putString(KEY_FOLDER_NAME, folderName)
      .remove(KEY_LAST_ERROR)
      .apply()
  }

  fun setWifiOnly(enabled: Boolean) {
    preferences.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
  }

  fun clearConfiguration() {
    preferences.edit()
      .remove(KEY_FOLDER_URI)
      .remove(KEY_FOLDER_NAME)
      .remove(KEY_LAST_ERROR)
      .apply()
  }

  fun recordSuccess(fileName: String) {
    preferences.edit()
      .putString(KEY_LAST_SUCCESS_AT, Instant.now().toString())
      .putString(KEY_LAST_FILE_NAME, fileName)
      .remove(KEY_LAST_ERROR)
      .apply()
  }

  fun recordFailure(error: Throwable) {
    val message = generateSequence(error) { it.cause }
      .mapNotNull(Throwable::message)
      .firstOrNull(String::isNotBlank)
      ?: error.javaClass.simpleName
    preferences.edit().putString(KEY_LAST_ERROR, message.take(500)).apply()
  }

  companion object {
    const val FILE_NAME = "google_drive_backup"
    internal const val KEY_WIFI_ONLY = "wifi_only"

    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_LAST_FILE_NAME = "last_file_name"
    private const val KEY_LAST_ERROR = "last_error"
  }
}
