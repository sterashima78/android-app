package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
) {
  private val amazonCoverClient = KindleAmazonCoverClient()
  private val googleBooksCoverClient = GoogleBooksCoverClient()
  private val openLibraryCoverClient = OpenLibraryCoverClient()
  private var schemaEnsured = false

  suspend fun enrichNext(): Boolean {
    ensureSchema()
    if (!isEnabled()) return false
    val book = queryCandidate() ?: return false
    saveLookup(book, lookup(book))
    return true
  }

  private suspend fun lookup(book: LibraryBook): KindleCoverEnrichmentLookupResult {
    val steps = mutableListOf<CoverLookupTraceStep>()
    var retryableFailure: CoverProviderIOException? = null

    val amazon = try {
      amazonCoverClient.lookup(book.sourceId).also { steps += it.traceStep }
    } catch (error: CoverProviderIOException) {
      steps += error.step
      retryableFailure = error
      null
    }
    if (amazon?.lookup?.status == CoverLookupStatus.FOUND) {
      return KindleCoverEnrichmentLookupResult(
        lookup = amazon.lookup,
        provider = amazon.provider,
        trace = steps.toDiagnosticTrace(),
      )
    }

    val googleBooks = try {
      googleBooksCoverClient.lookup(book).also { steps += it.step }
    } catch (error: CoverProviderIOException) {
      steps += error.step
      if (retryableFailure == null) retryableFailure = error
      null
    }
    if (googleBooks?.lookup?.status == CoverLookupStatus.FOUND) {
      return KindleCoverEnrichmentLookupResult(
        lookup = googleBooks.lookup,
        provider = KindleCoverProvider.GOOGLE_BOOKS,
        trace = steps.toDiagnosticTrace(),
      )
    }

    val openLibrary = try {
      openLibraryCoverClient.lookupWithTrace(book).also { steps += it.step }
    } catch (error: CoverProviderIOException) {
      steps += error.step
      if (retryableFailure == null) retryableFailure = error
      null
    }
    if (openLibrary?.lookup?.status == CoverLookupStatus.FOUND) {
      return KindleCoverEnrichmentLookupResult(
        lookup = openLibrary.lookup,
        provider = KindleCoverProvider.OPEN_LIBRARY,
        trace = steps.toDiagnosticTrace(),
      )
    }

    retryableFailure?.let { failure ->
      throw KindleCoverEnrichmentException(
        message = failure.message ?: "Kindle 表紙補完で一時的な取得エラーが発生しました",
        diagnosticTrace = steps.toDiagnosticTrace(),
        cause = failure,
      )
    }

    val unresolved = buildList {
      googleBooks?.let {
        add(ProviderLookup(KindleCoverProvider.GOOGLE_BOOKS, it.lookup))
      }
      openLibrary?.let {
        add(ProviderLookup(KindleCoverProvider.OPEN_LIBRARY, it.lookup))
      }
    }
    val strongest = unresolved.lastOrNull { it.lookup.status == CoverLookupStatus.AMBIGUOUS }
      ?: unresolved.lastOrNull { it.lookup.status == CoverLookupStatus.ERROR }
      ?: unresolved.lastOrNull()
      ?: amazon?.let { ProviderLookup(it.provider, it.lookup) }
      ?: ProviderLookup(
        KindleCoverProvider.OPEN_LIBRARY,
        CoverLookupResult(CoverLookupStatus.NOT_FOUND),
      )

    return KindleCoverEnrichmentLookupResult(
      lookup = strongest.lookup,
      provider = strongest.provider,
      trace = steps.toDiagnosticTrace(),
    )
  }

  private fun queryCandidate(): LibraryBook? {
    val staleBefore = System.currentTimeMillis() - COVER_LOOKUP_STALE_MILLIS
    return database.readable.rawQuery(
      """
        SELECT item.source_id, item.title, item.authors, item.isbn10, item.isbn13
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ?
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND (metadata.updated_at IS NULL OR metadata.updated_at < ?)
        ORDER BY item.title COLLATE NOCASE, item.source_id
        LIMIT 1
      """.trimIndent(),
      arrayOf(LibrarySource.KINDLE.name, staleBefore.toString()),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      val isbn10Index = cursor.getColumnIndexOrThrow("isbn10")
      val isbn13Index = cursor.getColumnIndexOrThrow("isbn13")
      LibraryBook(
        source = LibrarySource.KINDLE,
        sourceId = cursor.getString(cursor.getColumnIndexOrThrow("source_id")),
        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
        authors = parseKindleAuthors(cursor.getString(cursor.getColumnIndexOrThrow("authors"))),
        publisher = null,
        publishedDate = null,
        description = null,
        isbn10 = if (cursor.isNull(isbn10Index)) null else cursor.getString(isbn10Index),
        isbn13 = if (cursor.isNull(isbn13Index)) null else cursor.getString(isbn13Index),
        thumbnailUrl = null,
        infoUrl = null,
      )
    }
  }

  private fun saveLookup(book: LibraryBook, result: KindleCoverEnrichmentLookupResult) {
    val lookup = result.lookup
    val values = ContentValues().apply {
      put("source", LibrarySource.KINDLE.name)
      put("source_id", book.sourceId)
      lookup.thumbnailUrl?.let { put("thumbnail_url", it) } ?: putNull("thumbnail_url")
      put("provider", result.provider.storageValue)
      put("lookup_status", lookup.status.name)
      lookup.matchedIdentifier?.let { put("matched_identifier", it) } ?: putNull("matched_identifier")
      put(DIAGNOSTIC_TRACE_COLUMN, result.trace)
      put("updated_at", System.currentTimeMillis())
    }
    database.writable.insertWithOnConflict(
      "library_item_external_metadata",
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  private suspend fun ensureSchema() {
    if (schemaEnsured) return
    DefaultLibraryRepository(database).snapshot()
    ensureDiagnosticTraceColumn()
    schemaEnsured = true
  }

  private fun ensureDiagnosticTraceColumn() {
    if (hasDiagnosticTraceColumn()) return
    runCatching {
      database.writable.execSQL(
        "ALTER TABLE library_item_external_metadata ADD COLUMN $DIAGNOSTIC_TRACE_COLUMN TEXT",
      )
    }.getOrElse { error ->
      if (!hasDiagnosticTraceColumn()) throw error
    }
  }

  private fun hasDiagnosticTraceColumn(): Boolean = database.readable.rawQuery(
    "PRAGMA table_info(library_item_external_metadata)",
    null,
  ).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == DIAGNOSTIC_TRACE_COLUMN) return@use true
    }
    false
  }

  private fun isEnabled(): Boolean = database.readable.rawQuery(
    "SELECT value FROM library_settings WHERE key = ? LIMIT 1",
    arrayOf(KINDLE_COVER_ENRICHMENT_SETTING),
  ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }

  private companion object {
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val KINDLE_COVER_ENRICHMENT_SETTING = "kindle_cover_enrichment_enabled"
  }
}

private data class KindleCoverEnrichmentLookupResult(
  val lookup: CoverLookupResult,
  val provider: KindleCoverProvider,
  val trace: String,
)

private data class ProviderLookup(
  val provider: KindleCoverProvider,
  val lookup: CoverLookupResult,
)

private fun parseKindleAuthors(value: String): List<String> = runCatching {
  val array = JSONArray(value)
  buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}.getOrElse { emptyList() }
