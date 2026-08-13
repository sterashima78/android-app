package dev.terashima.yomitorirss.feature.library.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionItem
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionState
import dev.terashima.yomitorirss.feature.library.LibrarySource

class LibraryCoverStatusRepository(
  private val database: DatabaseConnection,
) {
  fun snapshot(nowEpochMillis: Long = System.currentTimeMillis()): LibraryCoverAcquisitionSnapshot {
    ensureSchema()
    val staleBefore = nowEpochMillis - COVER_LOOKUP_STALE_MILLIS
    val items = database.readable.rawQuery(
      """
        SELECT item.source, item.source_id, item.title,
               item.thumbnail_url AS source_thumbnail_url,
               metadata.thumbnail_url AS external_thumbnail_url,
               metadata.provider, metadata.lookup_status, metadata.updated_at
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source IN (?, ?)
        ORDER BY item.source, item.title COLLATE NOCASE, item.source_id
      """.trimIndent(),
      arrayOf(LibrarySource.KINDLE.name, LibrarySource.AUDIBLE.name),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val updatedAt = cursor.nullableLong("updated_at")
          add(
            LibraryCoverAcquisitionItem(
              source = LibrarySource.valueOf(cursor.string("source")),
              sourceId = cursor.string("source_id"),
              title = cursor.string("title"),
              state = resolveLibraryCoverAcquisitionState(
                sourceThumbnailUrl = cursor.nullableString("source_thumbnail_url"),
                externalThumbnailUrl = cursor.nullableString("external_thumbnail_url"),
                lookupStatus = cursor.nullableString("lookup_status"),
                updatedAtEpochMillis = updatedAt,
                staleBeforeEpochMillis = staleBefore,
              ),
              provider = cursor.nullableString("provider"),
              lastAttemptAtEpochMillis = updatedAt,
            ),
          )
        }
      }
    }
    return LibraryCoverAcquisitionSnapshot(items)
  }

  fun resetUnresolvedLookups() {
    ensureSchema()
    database.writable.delete(
      "library_item_external_metadata",
      """
        source IN (?, ?)
        AND (thumbnail_url IS NULL OR TRIM(thumbnail_url) = '')
      """.trimIndent(),
      arrayOf(LibrarySource.KINDLE.name, LibrarySource.AUDIBLE.name),
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

  private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

  private fun Cursor.nullableString(name: String): String? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getString(index)
  }

  private fun Cursor.nullableLong(name: String): Long? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getLong(index)
  }

  private companion object {
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
  }
}

internal fun resolveLibraryCoverAcquisitionState(
  sourceThumbnailUrl: String?,
  externalThumbnailUrl: String?,
  lookupStatus: String?,
  updatedAtEpochMillis: Long?,
  staleBeforeEpochMillis: Long,
): LibraryCoverAcquisitionState {
  if (!sourceThumbnailUrl.isNullOrBlank()) return LibraryCoverAcquisitionState.SOURCE_PROVIDED
  if (!externalThumbnailUrl.isNullOrBlank()) return LibraryCoverAcquisitionState.ACQUIRED
  if (updatedAtEpochMillis == null || updatedAtEpochMillis < staleBeforeEpochMillis) {
    return LibraryCoverAcquisitionState.WAITING
  }
  return when (lookupStatus) {
    CoverLookupStatus.NOT_FOUND.name -> LibraryCoverAcquisitionState.NOT_FOUND
    CoverLookupStatus.AMBIGUOUS.name -> LibraryCoverAcquisitionState.AMBIGUOUS
    else -> LibraryCoverAcquisitionState.WAITING
  }
}
