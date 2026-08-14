package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import kotlinx.coroutines.delay
import org.json.JSONArray

class AudibleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
  private val productPageClient: AudibleCoverClient = AudibleCoverClient(),
) {
  private val catalogClient = AudibleCatalogCoverClient()

  suspend fun enrichBatch(limit: Int = AUDIBLE_COVER_BATCH_SIZE): Boolean {
    require(limit > 0) { "表紙補完の処理件数は1件以上で指定してください" }
    ensureSchema()

    val candidates = queryCandidates(limit)
    candidates.forEachIndexed { index, candidate ->
      saveLookup(candidate.sourceId, lookup(candidate))
      if (index < candidates.lastIndex) delay(COVER_REQUEST_DELAY_MILLIS)
    }
    return candidates.size == limit
  }

  private suspend fun lookup(candidate: AudibleCoverCandidate): AudibleCoverLookupResult {
    var productPageFailure: IOException? = null
    try {
      val productPage = productPageClient.lookup(candidate.sourceId)
      if (productPage.status == CoverLookupStatus.FOUND) {
        return AudibleCoverLookupResult(
          lookup = productPage,
          provider = AudibleCoverProvider.PRODUCT_PAGE,
        )
      }
    } catch (error: IOException) {
      productPageFailure = error
    }

    val catalog = catalogClient.lookup(
      sourceId = candidate.sourceId,
      title = candidate.title,
      authors = candidate.authors,
    )
    if (productPageFailure != null && catalog.lookup.status != CoverLookupStatus.FOUND) {
      throw productPageFailure
    }
    return catalog
  }

  private fun queryCandidates(limit: Int): List<AudibleCoverCandidate> {
    val staleBefore = System.currentTimeMillis() - COVER_LOOKUP_STALE_MILLIS
    return database.readable.rawQuery(
      """
        SELECT item.source_id, item.title, item.authors
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
        val titleIndex = cursor.getColumnIndexOrThrow("title")
        val authorsIndex = cursor.getColumnIndexOrThrow("authors")
        while (cursor.moveToNext()) {
          add(
            AudibleCoverCandidate(
              sourceId = cursor.getString(sourceIdIndex),
              title = cursor.getString(titleIndex),
              authors = parseAuthors(cursor.getString(authorsIndex)),
            ),
          )
        }
      }
    }
  }

  private fun saveLookup(
    sourceId: String,
    result: AudibleCoverLookupResult,
  ) {
    val lookup = result.lookup
    val values = ContentValues().apply {
      put("source", LibrarySource.AUDIBLE.name)
      put("source_id", sourceId)
      lookup.thumbnailUrl?.let { put("thumbnail_url", it) } ?: putNull("thumbnail_url")
      put("provider", result.provider.storageValue)
      put("lookup_status", lookup.status.name)
      lookup.matchedIdentifier?.let { put("matched_identifier", it) } ?: putNull("matched_identifier")
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
  }
}

private data class AudibleCoverCandidate(
  val sourceId: String,
  val title: String,
  val authors: List<String>,
)

private fun parseAuthors(value: String): List<String> = runCatching {
  val array = JSONArray(value)
  buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}.getOrElse { emptyList() }
