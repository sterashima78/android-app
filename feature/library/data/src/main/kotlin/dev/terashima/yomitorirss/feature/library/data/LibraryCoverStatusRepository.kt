package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionItem
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionState
import dev.terashima.yomitorirss.feature.library.LibrarySource

class LibraryCoverStatusRepository(
  private val database: DatabaseConnection,
) {
  private var schemaEnsured = false

  suspend fun snapshot(nowEpochMillis: Long = System.currentTimeMillis()): LibraryCoverAcquisitionSnapshot {
    ensureSchema()
    val staleBefore = nowEpochMillis - COVER_LOOKUP_STALE_MILLIS
    val items = database.readable.rawQuery(
      """
        SELECT item.source, item.source_id, item.title,
               item.thumbnail_url AS source_thumbnail_url,
               metadata.thumbnail_url AS external_thumbnail_url,
               metadata.provider, metadata.lookup_status, metadata.updated_at,
               metadata.diagnostic_detail, metadata.diagnostic_trace,
               COALESCE(metadata.$RETRY_COUNT_COLUMN, 0) AS retry_count,
               metadata.$NEXT_ATTEMPT_AT_COLUMN AS next_attempt_at
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
          val nextAttemptAt = cursor.nullableLong("next_attempt_at")
          val provider = cursor.nullableString("provider")
          val diagnosticDetail = cursor.nullableString("diagnostic_detail")
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
                nextAttemptAtEpochMillis = nextAttemptAt,
              ),
              provider = provider.withDiagnosticDetail(diagnosticDetail),
              lastAttemptAtEpochMillis = updatedAt,
              diagnosticTrace = cursor.nullableString(DIAGNOSTIC_TRACE_COLUMN),
              retryCount = cursor.int("retry_count"),
              nextAttemptAtEpochMillis = nextAttemptAt,
            ),
          )
        }
      }
    }
    return LibraryCoverAcquisitionSnapshot(
      items = items,
      kindleCoverEnrichmentEnabled = isKindleCoverEnrichmentEnabled(),
    )
  }

  suspend fun markNextKindleCoverLookupError(
    detail: String? = null,
    diagnosticTrace: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
  ): Boolean = markNextCoverLookupError(
    source = LibrarySource.KINDLE,
    provider = KINDLE_COVER_ENRICHMENT_PROVIDER,
    orderBy = "item.title COLLATE NOCASE, item.source_id",
    detail = detail,
    diagnosticTrace = diagnosticTrace,
    nowEpochMillis = nowEpochMillis,
  )

  suspend fun markNextAudibleCoverLookupError(
    detail: String? = null,
    nowEpochMillis: Long = System.currentTimeMillis(),
  ): Boolean = markNextCoverLookupError(
    source = LibrarySource.AUDIBLE,
    provider = AUDIBLE_COVER_ENRICHMENT_PROVIDER,
    orderBy = "item.source_id",
    detail = detail,
    diagnosticTrace = null,
    nowEpochMillis = nowEpochMillis,
  )

  suspend fun resetUnresolvedLookups(sources: Set<LibrarySource>) {
    if (sources.isEmpty()) return
    ensureSchema()
    database.transaction {
      sources.forEach { source ->
        delete(
          "library_item_external_metadata",
          "source = ? AND (thumbnail_url IS NULL OR TRIM(thumbnail_url) = '')",
          arrayOf(source.name),
        )
      }
    }
  }

  private suspend fun markNextCoverLookupError(
    source: LibrarySource,
    provider: String,
    orderBy: String,
    detail: String?,
    diagnosticTrace: String?,
    nowEpochMillis: Long,
  ): Boolean {
    ensureSchema()
    val staleBefore = nowEpochMillis - COVER_LOOKUP_STALE_MILLIS
    val sourceId = database.readable.rawQuery(
      """
        SELECT item.source_id
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ?
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND (metadata.updated_at IS NULL OR metadata.updated_at < ?)
        ORDER BY $orderBy
        LIMIT 1
      """.trimIndent(),
      arrayOf(source.name, staleBefore.toString()),
    ).use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: return false

    val values = ContentValues().apply {
      put("source", source.name)
      put("source_id", sourceId)
      putNull("thumbnail_url")
      put("provider", provider)
      put("lookup_status", CoverLookupStatus.ERROR.name)
      putNull("matched_identifier")
      detail?.sanitizeDiagnosticText(MAX_DIAGNOSTIC_DETAIL_CHARS)
        ?.let { put(DIAGNOSTIC_DETAIL_COLUMN, it) }
        ?: putNull(DIAGNOSTIC_DETAIL_COLUMN)
      diagnosticTrace?.sanitizeDiagnosticText(MAX_DIAGNOSTIC_TRACE_CHARS)
        ?.let { put(DIAGNOSTIC_TRACE_COLUMN, it) }
        ?: putNull(DIAGNOSTIC_TRACE_COLUMN)
      put(RETRY_COUNT_COLUMN, 0)
      putNull(NEXT_ATTEMPT_AT_COLUMN)
      put("updated_at", nowEpochMillis)
    }
    database.writable.insertWithOnConflict(
      "library_item_external_metadata",
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
    return true
  }

  private suspend fun ensureSchema() {
    if (schemaEnsured) return
    DefaultLibraryRepository(database).snapshot()
    ensureColumn(DIAGNOSTIC_DETAIL_COLUMN, "TEXT")
    ensureColumn(DIAGNOSTIC_TRACE_COLUMN, "TEXT")
    ensureColumn(RETRY_COUNT_COLUMN, "INTEGER NOT NULL DEFAULT 0")
    ensureColumn(NEXT_ATTEMPT_AT_COLUMN, "INTEGER")
    schemaEnsured = true
  }

  private fun ensureColumn(column: String, definition: String) {
    if (hasColumn(column)) return
    runCatching {
      database.writable.execSQL(
        "ALTER TABLE library_item_external_metadata ADD COLUMN $column $definition",
      )
    }.getOrElse { error ->
      if (!hasColumn(column)) throw error
    }
  }

  private fun hasColumn(column: String): Boolean = database.readable.rawQuery(
    "PRAGMA table_info(library_item_external_metadata)",
    null,
  ).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == column) return@use true
    }
    false
  }

  private fun isKindleCoverEnrichmentEnabled(): Boolean {
    return database.readable.rawQuery(
      "SELECT value FROM library_settings WHERE key = ? LIMIT 1",
      arrayOf(KINDLE_COVER_ENRICHMENT_SETTING),
    ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }
  }

  private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
  private fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))

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
    const val KINDLE_COVER_ENRICHMENT_SETTING = "kindle_cover_enrichment_enabled"
    const val KINDLE_COVER_ENRICHMENT_PROVIDER = "KINDLE_COVER_ENRICHMENT"
    const val AUDIBLE_COVER_ENRICHMENT_PROVIDER = "AUDIBLE_COVER_ENRICHMENT"
    const val DIAGNOSTIC_DETAIL_COLUMN = "diagnostic_detail"
  }
}

internal fun resolveLibraryCoverAcquisitionState(
  sourceThumbnailUrl: String?,
  externalThumbnailUrl: String?,
  lookupStatus: String?,
  updatedAtEpochMillis: Long?,
  staleBeforeEpochMillis: Long,
  nextAttemptAtEpochMillis: Long? = null,
): LibraryCoverAcquisitionState {
  if (!sourceThumbnailUrl.isNullOrBlank()) return LibraryCoverAcquisitionState.SOURCE_PROVIDED
  if (!externalThumbnailUrl.isNullOrBlank()) return LibraryCoverAcquisitionState.ACQUIRED
  if (lookupStatus == CoverLookupStatus.ERROR.name) {
    if (nextAttemptAtEpochMillis != null || updatedAtEpochMillis == null || updatedAtEpochMillis >= staleBeforeEpochMillis) {
      return LibraryCoverAcquisitionState.ERROR
    }
    return LibraryCoverAcquisitionState.WAITING
  }
  if (updatedAtEpochMillis == null || updatedAtEpochMillis < staleBeforeEpochMillis) {
    return LibraryCoverAcquisitionState.WAITING
  }
  return when (lookupStatus) {
    CoverLookupStatus.NOT_FOUND.name -> LibraryCoverAcquisitionState.NOT_FOUND
    CoverLookupStatus.AMBIGUOUS.name -> LibraryCoverAcquisitionState.AMBIGUOUS
    else -> LibraryCoverAcquisitionState.WAITING
  }
}

private fun String?.withDiagnosticDetail(detail: String?): String? = when {
  detail.isNullOrBlank() -> this
  isNullOrBlank() -> detail
  else -> "$this · $detail"
}

private fun String.sanitizeDiagnosticText(maxChars: Int): String =
  replace(SENSITIVE_QUERY_PARAMETER, "$1<redacted>")
    .take(maxChars)

private val SENSITIVE_QUERY_PARAMETER = Regex(
  "(?i)([?&](?:access_token|api_key|apikey|key|token|signature|sig|authorization)=)[^&#\\s]*",
)
private const val MAX_DIAGNOSTIC_DETAIL_CHARS = 2_048
private const val MAX_DIAGNOSTIC_TRACE_CHARS = 8_192
