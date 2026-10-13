package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.MAX_SMB_METADATA_REANALYSIS_CONTEXT_CHARS
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchSnapshot
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationItem
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Read-only projections for the normalization queue.
internal class SmbMetadataNormalizationQueries(private val database: DatabaseConnection) {
  fun queryLatestItem(sourceId: String): SmbMetadataNormalizationItem? =
    queryLatestBatchSnapshot()?.items?.firstOrNull { it.sourceId == sourceId }

  fun queryLatestBatchSnapshot(): SmbMetadataNormalizationBatchSnapshot? =
    queryLatestBatchSnapshot(database.readable)

  fun queryLatestBatchSnapshot(db: SQLiteDatabase): SmbMetadataNormalizationBatchSnapshot? {
    val header = db.rawQuery(
      "SELECT batch_id, status, created_at, updated_at FROM $BATCH_TABLE ORDER BY created_at DESC LIMIT 1",
      null,
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      SmbMetadataNormalizationBatchHeader(
        batchId = cursor.getString(0),
        status = SmbMetadataNormalizationBatchStatus.valueOf(cursor.getString(1)),
        createdAt = cursor.getLong(2),
        updatedAt = cursor.getLong(3),
      )
    } ?: return null
    val covers = queryBookCovers(db)
    val currentItems = db.rawQuery(
      """
        SELECT source_id, original_file_name, input_size, input_modified_at, status,
               proposed_file_name, metadata_json, error, updated_at
        FROM $ITEM_TABLE
        WHERE batch_id = ?
        ORDER BY
          CASE status
            WHEN 'PROCESSING' THEN 0
            WHEN 'QUEUED' THEN 1
            WHEN 'WAITING_FOR_COVER' THEN 2
            WHEN 'PENDING_REVIEW' THEN 3
            WHEN 'DEFERRED' THEN 4
            WHEN 'FAILED' THEN 5
            WHEN 'SKIPPED' THEN 6
            ELSE 7
          END,
          updated_at DESC,
          original_file_name COLLATE NOCASE
      """.trimIndent(),
      arrayOf(header.batchId),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val metadata = if (cursor.isNull(6)) null else proposalFromJson(cursor.getString(6))
          add(
            SmbMetadataNormalizationItem(
              batchId = header.batchId,
              sourceId = cursor.getString(0),
              originalFileName = cursor.getString(1),
              inputSize = cursor.getLong(2),
              inputModifiedAt = cursor.getLong(3),
              status = SmbMetadataNormalizationStatus.valueOf(cursor.getString(4)),
              proposedFileName = if (cursor.isNull(5)) null else cursor.getString(5),
              proposal = metadata,
              coverUrl = covers[cursor.getString(0)],
              error = if (cursor.isNull(7)) null else cursor.getString(7),
              updatedAtEpochMillis = cursor.getLong(8),
            ),
          )
        }
      }
    }
    val historicalRejected = queryHistoricalRejectedItems(
      db = db,
      currentBatchId = header.batchId,
      currentSourceIds = currentItems.mapTo(mutableSetOf(), SmbMetadataNormalizationItem::sourceId),
      covers = covers,
    )
    return SmbMetadataNormalizationBatchSnapshot(
      batchId = header.batchId,
      status = header.status,
      items = currentItems + historicalRejected,
      createdAtEpochMillis = header.createdAt,
      updatedAtEpochMillis = header.updatedAt,
    )
  }

  fun queryHistoricalRejectedItems(
    db: SQLiteDatabase,
    currentBatchId: String,
    currentSourceIds: Set<String>,
    covers: Map<String, String?>,
  ): List<SmbMetadataNormalizationItem> = db.rawQuery(
    """
      SELECT i.batch_id, i.source_id, i.original_file_name, i.input_size, i.input_modified_at,
             i.proposed_file_name, i.metadata_json, i.error, i.updated_at
      FROM $ITEM_TABLE i
      JOIN $DECISION_TABLE d ON d.source_id = i.source_id
      WHERE d.decision_status = ? AND i.status = ? AND i.batch_id <> ?
      ORDER BY i.updated_at DESC
    """.trimIndent(),
    arrayOf(
      SmbMetadataNormalizationStatus.REJECTED.name,
      SmbMetadataNormalizationStatus.REJECTED.name,
      currentBatchId,
    ),
  ).use { cursor ->
    buildList {
      val seenSourceIds = currentSourceIds.toMutableSet()
      while (cursor.moveToNext()) {
        val sourceId = cursor.getString(1)
        if (!seenSourceIds.add(sourceId)) continue
        val metadata = if (cursor.isNull(6)) null else proposalFromJson(cursor.getString(6))
        add(
          SmbMetadataNormalizationItem(
            batchId = cursor.getString(0),
            sourceId = sourceId,
            originalFileName = cursor.getString(2),
            inputSize = cursor.getLong(3),
            inputModifiedAt = cursor.getLong(4),
            status = SmbMetadataNormalizationStatus.REJECTED,
            proposedFileName = if (cursor.isNull(5)) null else cursor.getString(5),
            proposal = metadata,
            coverUrl = covers[sourceId],
            error = if (cursor.isNull(7)) null else cursor.getString(7),
            updatedAtEpochMillis = cursor.getLong(8),
          ),
        )
      }
    }
  }

  fun queryConfirmedSourceIds(db: SQLiteDatabase): Set<String> = db.rawQuery(
    "SELECT source_id FROM $DECISION_TABLE WHERE decision_status IN (?, ?)",
    arrayOf(SmbMetadataNormalizationStatus.APPLIED.name, SmbMetadataNormalizationStatus.REJECTED.name),
  ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

  fun queryBookCover(sourceId: String): String? = queryBookCover(sourceId, database.readable)

  fun queryBookCover(sourceId: String, db: SQLiteDatabase): String? = db.rawQuery(
    "SELECT thumbnail_url FROM library_items WHERE source = ? AND source_id = ? LIMIT 1",
    arrayOf(LibrarySource.SMB.name, sourceId),
  ).use { cursor ->
    if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
  }

  fun queryBookCovers(db: SQLiteDatabase): Map<String, String?> = db.rawQuery(
    "SELECT source_id, thumbnail_url FROM library_items WHERE source = ?",
    arrayOf(LibrarySource.SMB.name),
  ).use { cursor ->
    buildMap {
      while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1))
    }
  }

}
