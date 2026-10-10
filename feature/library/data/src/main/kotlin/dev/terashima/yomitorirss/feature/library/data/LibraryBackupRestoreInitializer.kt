package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus

class LibraryBackupRestoreInitializer(
  private val database: DatabaseConnection,
  private val normalizationScheduler: SmbMetadataNormalizationScheduler? = null,
  private val coverPrefetchScheduler: SmbCoverPrefetchScheduler? = null,
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
      arrayOf(SMB_METADATA_NORMALIZATION_BATCH_TABLE),
    ).use { it.moveToFirst() }
    val hasNormalizationItemTable = database.readable.rawQuery(
      "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
      arrayOf(SMB_METADATA_NORMALIZATION_ITEM_TABLE),
    ).use { it.moveToFirst() }
    val hasRunningNormalizationBatch = hasNormalizationBatchTable && hasNormalizationItemTable && database.readable.rawQuery(
      "SELECT 1 FROM $SMB_METADATA_NORMALIZATION_BATCH_TABLE WHERE status=? LIMIT 1",
      arrayOf(SmbMetadataNormalizationBatchStatus.RUNNING.name),
    ).use { it.moveToFirst() }
    if (hasRunningNormalizationBatch) {
      val normalizationScheduler = checkNotNull(normalizationScheduler) {
        "An SMB metadata normalization scheduler is required to resume restored work"
      }
      val waitingForCoverSourceIds = database.readable.rawQuery(
        """
          SELECT i.source_id
          FROM $SMB_METADATA_NORMALIZATION_ITEM_TABLE i
          JOIN $SMB_METADATA_NORMALIZATION_BATCH_TABLE b ON b.batch_id = i.batch_id
          WHERE b.status = ? AND i.status = ?
        """.trimIndent(),
        arrayOf(
          SmbMetadataNormalizationBatchStatus.RUNNING.name,
          SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name,
        ),
      ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
      if (waitingForCoverSourceIds.isNotEmpty()) {
        val enqueued = SmbCoverPrefetchQueueStore(database).enqueueMissingForSources(
          waitingForCoverSourceIds,
          retrySkipped = true,
        )
        if (enqueued > 0) {
          checkNotNull(coverPrefetchScheduler) {
            "An SMB cover prefetch scheduler is required to resume restored cover work"
          }.enqueue()
        }
      }
      normalizationScheduler.kick()
    }
  }
}
