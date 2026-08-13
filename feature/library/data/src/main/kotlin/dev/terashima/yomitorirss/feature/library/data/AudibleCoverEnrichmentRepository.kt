package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.delay

class AudibleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
  private val coverClient: AudibleCoverClient = AudibleCoverClient(),
) {
  suspend fun enrichBatch(limit: Int = AUDIBLE_COVER_BATCH_SIZE): Boolean {
    require(limit > 0) { "表紙補完の処理件数は1件以上で指定してください" }
    ensureSchema()

    val sourceIds = queryCandidates(limit)
    sourceIds.forEachIndexed { index, sourceId ->
      saveLookup(sourceId, coverClient.lookup(sourceId))
      if (index < sourceIds.lastIndex) delay(COVER_REQUEST_DELAY_MILLIS)
    }
    return sourceIds.size == limit
  }

  private fun queryCandidates(limit: Int): List<String> {
    val staleBefore = System.currentTimeMillis() - COVER_LOOKUP_STALE_MILLIS
    return database.readable.rawQuery(
      """
        SELECT item.source_id
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ?
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND (metadata.updated_at IS NULL OR metadata.updated_at < ?)
        ORDER BY item.source_id
        LIMIT ?
      """.trimIndent(),
      arrayOf(
        LibrarySource.AUDIBLE.name,
        staleBefore.toString(),
        limit.toString(),
      ),
    ).use { cursor ->
      buildList {
        val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
        while (cursor.moveToNext()) add(cursor.getString(sourceIdIndex))
      }
    }
  }

  private fun saveLookup(
    sourceId: String,
    result: CoverLookupResult,
  ) {
    val values = ContentValues().apply {
      put("source", LibrarySource.AUDIBLE.name)
      put("source_id", sourceId)
      result.thumbnailUrl?.let { put("thumbnail_url", it) } ?: putNull("thumbnail_url")
      put("provider", AUDIBLE_PRODUCT_PAGE_PROVIDER)
      put("lookup_status", result.status.name)
      result.matchedIdentifier?.let { put("matched_identifier", it) } ?: putNull("matched_identifier")
      put("updated_at", System.currentTimeMillis())
    }
    database.writable.insertWithOnConflict(
      "library_item_external_metadata",
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  private fun ensureSchema() {
    database.writable.execSQL(
      """
        CREATE TABLE IF NOT EXISTS library_item_external_metadata(
          source TEXT NOT NULL,
          source_id TEXT NOT NULL,
          thumbnail_url TEXT,
          provider TEXT NOT NULL,
          lookup_status TEXT NOT NULL,
          matched_identifier TEXT,
          updated_at INTEGER NOT NULL,
          PRIMARY KEY(source, source_id)
        )
      """.trimIndent(),
    )
  }

  private companion object {
    const val AUDIBLE_COVER_BATCH_SIZE = 5
    const val COVER_REQUEST_DELAY_MILLIS = 1_000L
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val AUDIBLE_PRODUCT_PAGE_PROVIDER = "AUDIBLE_PRODUCT_PAGE"
  }
}
