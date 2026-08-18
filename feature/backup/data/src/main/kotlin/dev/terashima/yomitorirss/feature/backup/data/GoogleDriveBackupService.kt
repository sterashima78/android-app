package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveBackupService(
  context: Context,
  database: YomitoriDatabase,
) {
  private val appContext = context.applicationContext
  private val preferences = GoogleDriveBackupPreferences(appContext)
  private val store = GoogleDriveBackupStore(appContext)
  private val archive = DatabaseBackupArchive(appContext, database)

  suspend fun backup(): String = withContext(Dispatchers.IO) {
    try {
      val fileName = store.write(archive)
      preferences.recordSuccess(fileName)
      fileName
    } catch (error: Throwable) {
      preferences.recordFailure(error)
      throw error
    }
  }
}
