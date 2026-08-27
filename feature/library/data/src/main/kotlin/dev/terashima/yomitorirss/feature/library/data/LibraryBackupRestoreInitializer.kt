package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource

class LibraryBackupRestoreInitializer(
  private val database: DatabaseConnection,
) {
  fun initialize() {
    database.localTransaction {
      update(
        "library_items",
        ContentValues().apply { putNull("thumbnail_url") },
        "source = ? AND thumbnail_url LIKE ?",
        arrayOf(LibrarySource.SMB.name, "file:%"),
      )
      delete("smb_cover_prefetch_queue", null, null)
    }
  }
}
