package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class CoverLookupDiagnosticEvent(
  val provider: String? = null,
  val stage: String,
  val outcome: String,
  val requestUrl: String? = null,
  val finalUrl: String? = null,
  val httpStatus: Int? = null,
  val contentType: String? = null,
  val bodyExcerpt: String? = null,
  val detail: String? = null,
)

internal class CoverLookupDiagnosticsSession(
  private val repository: LibraryCoverDiagnosticsRepository,
  private val source: LibrarySource,
  private val sourceId: String,
  private val startedAtEpochMillis: Long,
) {
  private var sequence = 0

  fun record(event: CoverLookupDiagnosticEvent) {
    repository.insert(
      source = source,
      sourceId = sourceId,
      sequence = sequence++,
      event = event,
      createdAtEpochMillis = System.currentTimeMillis().coerceAtLeast(startedAtEpochMillis),
    )
  }

  fun decision(
    provider: String? = null,
    stage: String,
    outcome: String,
    detail: String? = null,
  ) {
    record(
      CoverLookupDiagnosticEvent(
        provider = provider,
        stage = stage,
        outcome = outcome,
        detail = detail,
      ),
    )
  }

  fun response(
    provider: String,
    stage: String,
    requestUrl: String,
    response: HttpResponse,
  ) {
    val contentType = response.header("Content-Type")
    record(
      CoverLookupDiagnosticEvent(
        provider = provider,
        stage = stage,
        outcome = "HTTP_RESPONSE",
        requestUrl = sanitizeDiagnosticUrl(requestUrl),
        finalUrl = sanitizeDiagnosticUrl(response.finalUrl),
        httpStatus = response.statusCode,
        contentType = contentType,
        bodyExcerpt = diagnosticBodyExcerpt(contentType, response.body),
        detail = "reason=${response.reasonPhrase}; bodyBytes=${response.body.size}",
      ),
    )
  }

  fun error(
    provider: String? = null,
    stage: String,
    error: Throwable,
  ) {
    record(
      CoverLookupDiagnosticEvent(
        provider = provider,
        stage = stage,
        outcome = "ERROR",
        detail = buildString {
          append(error::class.java.name)
          error.message?.let { message ->
            append(": ")
            append(message)
          }
        },
      ),
    )
  }
}

internal class LibraryCoverDiagnosticsRepository(
  private val database: DatabaseConnection,
) {
  fun ensureSchema() {
    database.writable.execSQL(
      """
        CREATE TABLE IF NOT EXISTS library_cover_lookup_diagnostics(
          source TEXT NOT NULL,
          source_id TEXT NOT NULL,
          sequence INTEGER NOT NULL,
          provider TEXT,
          stage TEXT NOT NULL,
          outcome TEXT NOT NULL,
          request_url TEXT,
          final_url TEXT,
          http_status INTEGER,
          content_type TEXT,
          body_excerpt TEXT,
          detail TEXT,
          created_at INTEGER NOT NULL,
          PRIMARY KEY(source, source_id, sequence)
        )
      """.trimIndent(),
    )
    database.writable.execSQL(
      """
        CREATE INDEX IF NOT EXISTS index_library_cover_lookup_diagnostics_created_at
        ON library_cover_lookup_diagnostics(created_at)
      """.trimIndent(),
    )
  }

  fun begin(
    source: LibrarySource,
    sourceId: String,
    inputDetail: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
  ): CoverLookupDiagnosticsSession {
    ensureSchema()
    cleanupOrphans()
    database.writable.delete(
      TABLE_NAME,
      "source = ? AND source_id = ?",
      arrayOf(source.name, sourceId),
    )
    return CoverLookupDiagnosticsSession(
      repository = this,
      source = source,
      sourceId = sourceId,
      startedAtEpochMillis = nowEpochMillis,
    ).also { session ->
      session.decision(
        stage = "pipeline.input",
        outcome = "STARTED",
        detail = inputDetail,
      )
    }
  }

  fun eventCount(source: LibrarySource, sourceId: String): Int {
    ensureSchema()
    return database.readable.rawQuery(
      "SELECT COUNT(*) FROM $TABLE_NAME WHERE source = ? AND source_id = ?",
      arrayOf(source.name, sourceId),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
  }

  fun report(source: LibrarySource, sourceId: String): String? {
    ensureSchema()
    val item = reportItem(source, sourceId) ?: return null
    return reportRoot(JSONArray().put(item)).toString(2)
  }

  fun unresolvedReport(): String? {
    ensureSchema()
    val items = database.readable.rawQuery(
      """
        SELECT DISTINCT item.source, item.source_id
        FROM library_items AS item
        JOIN library_cover_lookup_diagnostics AS diagnostic
          ON diagnostic.source = item.source AND diagnostic.source_id = item.source_id
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source IN (?, ?)
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND metadata.lookup_status IN (?, ?, ?)
        ORDER BY item.source, item.title COLLATE NOCASE, item.source_id
      """.trimIndent(),
      arrayOf(
        LibrarySource.KINDLE.name,
        LibrarySource.AUDIBLE.name,
        CoverLookupStatus.NOT_FOUND.name,
        CoverLookupStatus.AMBIGUOUS.name,
        CoverLookupStatus.ERROR.name,
      ),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val source = runCatching { LibrarySource.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
          reportItem(source, cursor.getString(1))?.let(::add)
        }
      }
    }
    if (items.isEmpty()) return null
    return reportRoot(JSONArray(items)).toString(2)
  }

  internal fun insert(
    source: LibrarySource,
    sourceId: String,
    sequence: Int,
    event: CoverLookupDiagnosticEvent,
    createdAtEpochMillis: Long,
  ) {
    ensureSchema()
    val values = ContentValues().apply {
      put("source", source.name)
      put("source_id", sourceId)
      put("sequence", sequence)
      event.provider?.let { put("provider", it) } ?: putNull("provider")
      put("stage", event.stage)
      put("outcome", event.outcome)
      event.requestUrl?.let { put("request_url", it) } ?: putNull("request_url")
      event.finalUrl?.let { put("final_url", it) } ?: putNull("final_url")
      event.httpStatus?.let { put("http_status", it) } ?: putNull("http_status")
      event.contentType?.let { put("content_type", it) } ?: putNull("content_type")
      event.bodyExcerpt?.let { put("body_excerpt", it) } ?: putNull("body_excerpt")
      event.detail?.let { put("detail", it) } ?: putNull("detail")
      put("created_at", createdAtEpochMillis)
    }
    database.writable.insertWithOnConflict(
      TABLE_NAME,
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  private fun reportRoot(items: JSONArray): JSONObject = JSONObject()
    .put("format", "yomitori-library-cover-diagnostics")
    .put("version", 1)
    .put("generatedAtEpochMillis", System.currentTimeMillis())
    .put(
      "privacyNotice",
      "このファイルはユーザーが明示的に保存した診断情報です。リクエストヘッダーやCookieは保存しません。URL内の一般的な認証パラメータは伏字化しています。レスポンス本文は解析用に一部を含むため、共有先を確認してください。",
    )
    .put("items", items)

  private fun reportItem(source: LibrarySource, sourceId: String): JSONObject? {
    val book = database.readable.rawQuery(
      """
        SELECT item.title, item.authors, item.isbn10, item.isbn13,
               metadata.provider, metadata.lookup_status, metadata.matched_identifier,
               metadata.updated_at
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ? AND item.source_id = ?
        LIMIT 1
      """.trimIndent(),
      arrayOf(source.name, sourceId),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      JSONObject()
        .put("source", source.name)
        .put("sourceId", sourceId)
        .put("title", cursor.getString(0))
        .put("authors", parseStoredJsonArray(cursor.nullableString(1)))
        .putNullable("isbn10", cursor.nullableString(2))
        .putNullable("isbn13", cursor.nullableString(3))
        .put(
          "latestResult",
          JSONObject()
            .putNullable("provider", cursor.nullableString(4))
            .putNullable("lookupStatus", cursor.nullableString(5))
            .putNullable("matchedIdentifier", cursor.nullableString(6))
            .putNullable("updatedAtEpochMillis", cursor.nullableLong(7)),
        )
    } ?: return null

    val events = database.readable.rawQuery(
      """
        SELECT sequence, provider, stage, outcome, request_url, final_url,
               http_status, content_type, body_excerpt, detail, created_at
        FROM $TABLE_NAME
        WHERE source = ? AND source_id = ?
        ORDER BY sequence
      """.trimIndent(),
      arrayOf(source.name, sourceId),
    ).use { cursor ->
      JSONArray().also { array ->
        while (cursor.moveToNext()) {
          array.put(
            JSONObject()
              .put("sequence", cursor.getInt(0))
              .putNullable("provider", cursor.nullableString(1))
              .put("stage", cursor.getString(2))
              .put("outcome", cursor.getString(3))
              .putNullable("requestUrl", cursor.nullableString(4))
              .putNullable("finalUrl", cursor.nullableString(5))
              .putNullable("httpStatus", cursor.nullableInt(6))
              .putNullable("contentType", cursor.nullableString(7))
              .putNullable("bodyExcerpt", cursor.nullableString(8))
              .putNullable("detail", cursor.nullableString(9))
              .put("createdAtEpochMillis", cursor.getLong(10)),
          )
        }
      }
    }
    return book.put("events", events)
  }

  private fun cleanupOrphans() {
    database.writable.execSQL(
      """
        DELETE FROM $TABLE_NAME
        WHERE NOT EXISTS (
          SELECT 1 FROM library_items AS item
          WHERE item.source = $TABLE_NAME.source
            AND item.source_id = $TABLE_NAME.source_id
        )
      """.trimIndent(),
    )
  }

  private fun Cursor.nullableString(index: Int): String? =
    if (isNull(index)) null else getString(index)

  private fun Cursor.nullableInt(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

  private fun Cursor.nullableLong(index: Int): Long? =
    if (isNull(index)) null else getLong(index)

  private companion object {
    const val TABLE_NAME = "library_cover_lookup_diagnostics"
  }
}

internal fun sanitizeDiagnosticUrl(url: String?): String? = url?.let { value ->
  SENSITIVE_QUERY_PARAMETER.replace(value) { match ->
    "${match.groupValues[1]}<redacted>"
  }
}

internal fun diagnosticBodyExcerpt(contentType: String?, body: ByteArray): String? {
  val mediaType = contentType
    ?.substringBefore(';')
    ?.trim()
    ?.lowercase(Locale.ROOT)
  if (mediaType != null && mediaType !in DIAGNOSTIC_TEXT_MEDIA_TYPES && !mediaType.startsWith("text/")) {
    return null
  }
  val text = body.toString(Charsets.UTF_8)
  if (text.length <= MAX_DIAGNOSTIC_BODY_CHARS) return text
  val omitted = text.length - MAX_DIAGNOSTIC_BODY_CHARS
  return text.take(MAX_DIAGNOSTIC_BODY_CHARS) + "\n…[truncated $omitted chars]"
}

private fun parseStoredJsonArray(value: String?): JSONArray = value?.let { text ->
  runCatching { JSONArray(text) }.getOrNull()
} ?: JSONArray()

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
  put(name, value ?: JSONObject.NULL)

private val SENSITIVE_QUERY_PARAMETER = Regex(
  "(?i)([?&](?:access_token|api_key|apikey|key|token|signature|sig|authorization)=)[^&#]*",
)
private val DIAGNOSTIC_TEXT_MEDIA_TYPES = setOf(
  "application/json",
  "application/problem+json",
  "application/xhtml+xml",
  "application/xml",
)
private const val MAX_DIAGNOSTIC_BODY_CHARS = 32 * 1024
