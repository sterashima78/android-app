package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler

class LibraryBackupRestoreInitializer(
  private val database: DatabaseConnection,
  private val normalizationScheduler: SmbMetadataNormalizationScheduler? = null,
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

    val hasNormalizationBatchTable = database.readable.rawQuery(
      "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
      arrayOf("smb_metadata_normalization_batches"),
    ).use { it.moveToFirst() }
    val hasRunningNormalizationBatch = hasNormalizationBatchTable && database.readable.rawQuery(
      "SELECT 1 FROM smb_metadata_normalization_batches WHERE status=? LIMIT 1",
      arrayOf(SmbMetadataNormalizationBatchStatus.RUNNING.name),
    ).use { it.moveToFirst() }
    if (hasRunningNormalizationBatch) {
      checkNotNull(normalizationScheduler) {
        "An SMB metadata normalization scheduler is required to resume restored work"
      }.kick()
    }
  }
}
