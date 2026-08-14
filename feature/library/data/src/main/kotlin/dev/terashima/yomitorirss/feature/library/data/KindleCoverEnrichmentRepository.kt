package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
  private val googleBooksAccessTokenProvider: suspend () -> String? = { null },
  private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
  private val amazonCoverClient = KindleAmazonCoverClient()
  private val googleBooksCoverClient = GoogleBooksCoverClient()
  private val ndlSearchBibliographicClient = NdlSearchBibliographicClient()
  private val openLibraryCoverClient = OpenLibraryCoverClient()
  private var schemaEnsured = false

  suspend fun enrichNext(): Boolean {
    ensureSchema()
    if (!isEnabled()) return false
    val now = nowEpochMillis()
    val candidate = queryCandidate(now) ?: return false
    val accessToken = googleBooksAccessTokenProvider()
    saveLookup(candidate, lookup(candidate.book, accessToken), now)
    return true
  }

  suspend fun nextWakeDelayMillis(now: Long = nowEpochMillis()): Long? {
    ensureSchema()
    val nextAttemptAt = database.readable.rawQuery(
      """
        SELECT MIN(metadata.$NEXT_ATTEMPT_AT_COLUMN)
        FROM library_items AS item
        JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ?
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND metadata.lookup_status = ?
          AND metadata.$NEXT_ATTEMPT_AT_COLUMN IS NOT NULL
          AND metadata.$NEXT_ATTEMPT_AT_COLUMN > ?
      """.trimIndent(),
      arrayOf(LibrarySource.KINDLE.name, CoverLookupStatus.ERROR.name, now.toString()),
    ).use { cursor ->
      if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    } ?: return null
    return (nextAttemptAt - now).coerceAtLeast(0L)
  }

  private suspend fun lookup(
    book: LibraryBook,
    accessToken: String?,
  ): KindleCoverEnrichmentLookupResult {
    val steps = mutableListOf<CoverLookupTraceStep>()
    val resolvedIdentifiers = mutableListOf<ResolvedBookIdentifier>()
    val unresolved = mutableListOf<ProviderLookup>()
    val retryableErrors = mutableListOf<String>()
    val terminalErrors = mutableListOf<String>()

    fun record(
      result: TracedCoverLookupResult,
      provider: String,
    ): KindleCoverEnrichmentLookupResult? {
      steps += result.step
      resolvedIdentifiers += result.resolvedIdentifiers
      when (result.lookup.status) {
        CoverLookupStatus.FOUND -> return KindleCoverEnrichmentLookupResult(
          lookup = result.lookup,
          provider = provider,
          steps = steps.toList(),
          resolvedIdentifiers = resolvedIdentifiers.distinctIdentifiers(),
        )
        CoverLookupStatus.AMBIGUOUS,
        CoverLookupStatus.NOT_FOUND,
        -> unresolved += ProviderLookup(provider, result.lookup)
        CoverLookupStatus.ERROR -> if (result.step.reason != "AUTH_UNAVAILABLE") {
          terminalErrors += "${result.step.provider}:${result.step.reason}"
        }
      }
      return null
    }

    fun recordProviderException(error: CoverProviderIOException) {
      steps += error.step
      if (isRetryableProviderFailure(error.step)) {
        retryableErrors += error.message ?: error.step.reason
      } else {
        terminalErrors += error.message ?: error.step.reason
      }
    }

    fun recordNetworkException(provider: String, error: IOException) {
      steps += CoverLookupTraceStep(
        provider = provider,
        status = CoverLookupStatus.ERROR,
        reason = "NETWORK_IO",
        retryable = true,
      )
      retryableErrors += error.message ?: "network error"
    }

    val amazon = try {
      amazonCoverClient.lookup(book.sourceId)
    } catch (error: CancellationException) {
      throw error
    } catch (error: CoverProviderIOException) {
      recordProviderException(error)
      null
    } catch (error: IOException) {
      recordNetworkException(AMAZON_PROVIDER_NAME, error)
      null
    }
    if (amazon != null) {
      record(
        TracedCoverLookupResult(amazon.lookup, amazon.traceStep),
        amazon.provider.storageValue,
      )?.let { return it }
    }

    val originalIsbn = book.isbn13.cleanBookIsbn() ?: book.isbn10.cleanBookIsbn()
    var resolvedIsbn: String? = originalIsbn

    if (originalIsbn != null) {
      val google = try {
        googleBooksCoverClient.lookupByIsbn(originalIsbn, accessToken)
      } catch (error: CancellationException) {
        throw error
      } catch (error: CoverProviderIOException) {
        recordProviderException(error)
        null
      } catch (error: IOException) {
        recordNetworkException(GOOGLE_BOOKS_PROVIDER, error)
        null
      }
      google?.let { result ->
        record(result, GOOGLE_BOOKS_PROVIDER)?.let { return it }
      }
    } else {
      var googleTitleUnavailable = false
      val google = try {
        googleBooksCoverClient.lookupByTitle(book, accessToken)
      } catch (error: CancellationException) {
        throw error
      } catch (error: CoverProviderIOException) {
        googleTitleUnavailable = true
        recordProviderException(error)
        null
      } catch (error: IOException) {
        googleTitleUnavailable = true
        recordNetworkException(GOOGLE_BOOKS_PROVIDER, error)
        null
      }
      google?.let { result ->
        if (result.lookup.status == CoverLookupStatus.ERROR) googleTitleUnavailable = true
        record(result, GOOGLE_BOOKS_PROVIDER)?.let { return it }
        resolvedIsbn = result.resolvedIdentifiers.preferredIsbn()
      }

      if (resolvedIsbn == null && isLikelyJapaneseBookTitle(book.title)) {
        val ndl = try {
          ndlSearchBibliographicClient.lookupByTitle(book)
        } catch (error: CancellationException) {
          throw error
        } catch (error: CoverProviderIOException) {
          recordProviderException(error)
          null
        } catch (error: IOException) {
          recordNetworkException(NDL_SEARCH_PROVIDER, error)
          null
        }
        ndl?.let { result ->
          record(result, NDL_SEARCH_PROVIDER)?.let { return it }
          resolvedIsbn = result.resolvedIdentifiers.preferredIsbn()
        }
      }

      if (resolvedIsbn != null && !accessToken.isNullOrBlank() && !googleTitleUnavailable) {
        val googleByIsbn = try {
          googleBooksCoverClient.lookupByIsbn(requireNotNull(resolvedIsbn), accessToken)
        } catch (error: CancellationException) {
          throw error
        } catch (error: CoverProviderIOException) {
          recordProviderException(error)
          null
        } catch (error: IOException) {
          recordNetworkException(GOOGLE_BOOKS_PROVIDER, error)
          null
        }
        googleByIsbn?.let { result ->
          record(result, GOOGLE_BOOKS_PROVIDER)?.let { return it }
        }
      }
    }

    if (resolvedIsbn != null) {
      val openLibraryByIsbn = try {
        openLibraryCoverClient.lookupByIsbn(requireNotNull(resolvedIsbn))
      } catch (error: CancellationException) {
        throw error
      } catch (error: CoverProviderIOException) {
        recordProviderException(error)
        null
      } catch (error: IOException) {
        recordNetworkException(OPEN_LIBRARY_PROVIDER, error)
        null
      }
      openLibraryByIsbn?.let { result ->
        record(result, OPEN_LIBRARY_PROVIDER)?.let { return it }
      }
    }

    val openLibraryByTitle = try {
      openLibraryCoverClient.lookupByTitle(book)
    } catch (error: CancellationException) {
      throw error
    } catch (error: CoverProviderIOException) {
      recordProviderException(error)
      null
    } catch (error: IOException) {
      recordNetworkException(OPEN_LIBRARY_PROVIDER, error)
      null
    }
    openLibraryByTitle?.let { result ->
      record(result, OPEN_LIBRARY_PROVIDER)?.let { return it }
    }

    val identifiers = resolvedIdentifiers.distinctIdentifiers()
    if (retryableErrors.isNotEmpty()) {
      return KindleCoverEnrichmentLookupResult(
        lookup = CoverLookupResult(CoverLookupStatus.ERROR),
        provider = KINDLE_COVER_ENRICHMENT_PROVIDER,
        steps = steps,
        resolvedIdentifiers = identifiers,
        retryable = true,
        errorDetail = retryableErrors.firstOrNull(),
      )
    }
    if (terminalErrors.isNotEmpty()) {
      return KindleCoverEnrichmentLookupResult(
        lookup = CoverLookupResult(CoverLookupStatus.ERROR),
        provider = KINDLE_COVER_ENRICHMENT_PROVIDER,
        steps = steps,
        resolvedIdentifiers = identifiers,
        retryable = false,
        errorDetail = terminalErrors.firstOrNull(),
      )
    }

    val strongest = unresolved.lastOrNull { it.lookup.status == CoverLookupStatus.AMBIGUOUS }
      ?: unresolved.lastOrNull()
      ?: ProviderLookup(OPEN_LIBRARY_PROVIDER, CoverLookupResult(CoverLookupStatus.NOT_FOUND))
    return KindleCoverEnrichmentLookupResult(
      lookup = strongest.lookup,
      provider = strongest.provider,
      steps = steps,
      resolvedIdentifiers = identifiers,
    )
  }

  private fun queryCandidate(now: Long): KindleCoverCandidate? {
    val staleBefore = now - COVER_LOOKUP_STALE_MILLIS
    return database.readable.rawQuery(
      """
        SELECT item.source_id, item.title, item.authors, item.isbn10, item.isbn13,
               COALESCE(metadata.$RETRY_COUNT_COLUMN, 0) AS retry_count
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
        WHERE item.source = ?
          AND (item.thumbnail_url IS NULL OR TRIM(item.thumbnail_url) = '')
          AND (metadata.thumbnail_url IS NULL OR TRIM(metadata.thumbnail_url) = '')
          AND (
            metadata.source_id IS NULL
            OR metadata.lookup_status IS NULL
            OR (metadata.lookup_status IN (?, ?) AND metadata.updated_at < ?)
            OR (
              metadata.lookup_status = ?
              AND (metadata.$NEXT_ATTEMPT_AT_COLUMN IS NULL OR metadata.$NEXT_ATTEMPT_AT_COLUMN <= ?)
            )
          )
        ORDER BY item.title COLLATE NOCASE, item.source_id
        LIMIT 1
      """.trimIndent(),
      arrayOf(
        LibrarySource.KINDLE.name,
        CoverLookupStatus.NOT_FOUND.name,
        CoverLookupStatus.AMBIGUOUS.name,
        staleBefore.toString(),
        CoverLookupStatus.ERROR.name,
        now.toString(),
      ),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      val isbn10Index = cursor.getColumnIndexOrThrow("isbn10")
      val isbn13Index = cursor.getColumnIndexOrThrow("isbn13")
      KindleCoverCandidate(
        book = LibraryBook(
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
        ),
        retryCount = cursor.getInt(cursor.getColumnIndexOrThrow("retry_count")),
      )
    }
  }

  private fun saveLookup(
    candidate: KindleCoverCandidate,
    result: KindleCoverEnrichmentLookupResult,
    now: Long,
  ) {
    val lookup = result.lookup
    val retryCount: Int
    val nextAttemptAt: Long?
    if (lookup.status == CoverLookupStatus.ERROR) {
      if (result.retryable) {
        retryCount = (candidate.retryCount + 1).coerceAtMost(MAX_RETRY_COUNT)
        nextAttemptAt = now + kindleCoverRetryDelayMillis(retryCount, result.steps)
      } else {
        retryCount = 0
        nextAttemptAt = now + COVER_LOOKUP_STALE_MILLIS
      }
    } else {
      retryCount = 0
      nextAttemptAt = null
    }

    val trace = result.steps.toDiagnosticTrace(
      resolvedIdentifiers = result.resolvedIdentifiers,
      nextAttemptAtEpochMillis = nextAttemptAt,
    )
    val values = ContentValues().apply {
      put("source", LibrarySource.KINDLE.name)
      put("source_id", candidate.book.sourceId)
      lookup.thumbnailUrl?.let { put("thumbnail_url", it) } ?: putNull("thumbnail_url")
      put("provider", result.provider)
      put("lookup_status", lookup.status.name)
      lookup.matchedIdentifier?.let { put("matched_identifier", it) } ?: putNull("matched_identifier")
      result.errorDetail?.take(MAX_DIAGNOSTIC_DETAIL_CHARS)?.let { put(DIAGNOSTIC_DETAIL_COLUMN, it) }
        ?: putNull(DIAGNOSTIC_DETAIL_COLUMN)
      put(DIAGNOSTIC_TRACE_COLUMN, trace)
      put(RETRY_COUNT_COLUMN, retryCount)
      nextAttemptAt?.let { put(NEXT_ATTEMPT_AT_COLUMN, it) } ?: putNull(NEXT_ATTEMPT_AT_COLUMN)
      put("updated_at", now)
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

  private fun isEnabled(): Boolean = database.readable.rawQuery(
    "SELECT value FROM library_settings WHERE key = ? LIMIT 1",
    arrayOf(KINDLE_COVER_ENRICHMENT_SETTING),
  ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }

  private companion object {
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val KINDLE_COVER_ENRICHMENT_SETTING = "kindle_cover_enrichment_enabled"
    const val KINDLE_COVER_ENRICHMENT_PROVIDER = "KINDLE_COVER_ENRICHMENT"
    const val AMAZON_PROVIDER_NAME = "AMAZON_PRODUCT_PAGE"
    const val DIAGNOSTIC_DETAIL_COLUMN = "diagnostic_detail"
    const val MAX_DIAGNOSTIC_DETAIL_CHARS = 2_048
    const val MAX_RETRY_COUNT = 3
  }
}

internal fun kindleCoverRetryDelayMillis(
  retryCount: Int,
  steps: List<CoverLookupTraceStep>,
): Long {
  if (steps.any { it.reason == "CHALLENGE_PAGE" }) return CHALLENGE_RETRY_DELAY_MILLIS
  steps.mapNotNull(CoverLookupTraceStep::retryAfterSeconds)
    .maxOrNull()
    ?.let { seconds -> return (seconds * 1_000L).coerceAtLeast(MIN_RETRY_DELAY_MILLIS) }
  return when (retryCount.coerceAtLeast(1)) {
    1 -> FIRST_RETRY_DELAY_MILLIS
    2 -> SECOND_RETRY_DELAY_MILLIS
    else -> MAX_RETRY_DELAY_MILLIS
  }
}

private fun isRetryableProviderFailure(step: CoverLookupTraceStep): Boolean =
  step.retryable || step.reason in setOf(
    "CHALLENGE_PAGE",
    "UNEXPECTED_REDIRECT",
    "INVALID_CONTENT_TYPE",
    "RESPONSE_TOO_LARGE",
    "PARSE_ERROR",
  ) || (step.reason == "HTTP_RETRYABLE")

private fun List<ResolvedBookIdentifier>.preferredIsbn(): String? =
  firstOrNull { it.type == "ISBN_13" }?.value
    ?: firstOrNull { it.type == "ISBN_10" }?.value

private fun List<ResolvedBookIdentifier>.distinctIdentifiers(): List<ResolvedBookIdentifier> =
  distinctBy { "${it.type}:${it.value}:${it.relation}:${it.source}" }

private data class KindleCoverCandidate(
  val book: LibraryBook,
  val retryCount: Int,
)

private data class KindleCoverEnrichmentLookupResult(
  val lookup: CoverLookupResult,
  val provider: String,
  val steps: List<CoverLookupTraceStep>,
  val resolvedIdentifiers: List<ResolvedBookIdentifier> = emptyList(),
  val retryable: Boolean = false,
  val errorDetail: String? = null,
)

private data class ProviderLookup(
  val provider: String,
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

private const val MIN_RETRY_DELAY_MILLIS = 60_000L
private const val FIRST_RETRY_DELAY_MILLIS = 15L * 60 * 1000
private const val SECOND_RETRY_DELAY_MILLIS = 2L * 60 * 60 * 1000
private const val MAX_RETRY_DELAY_MILLIS = 24L * 60 * 60 * 1000
private const val CHALLENGE_RETRY_DELAY_MILLIS = 24L * 60 * 60 * 1000
