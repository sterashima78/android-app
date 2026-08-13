package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import kotlinx.coroutines.delay
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
  private val amazonCoverClient: KindleAmazonCoverClient = KindleAmazonCoverClient(),
  private val openLibraryCoverClient: OpenLibraryCoverClient = OpenLibraryCoverClient(),
) {
  private var schemaEnsured = false

  suspend fun enrichBatch(limit: Int = KINDLE_COVER_BATCH_SIZE): Boolean {
    require(limit > 0) { "表紙補完の処理件数は1件以上で指定してください" }
    ensureSchema()
    if (!isEnabled()) return false

    val books = queryCandidates(limit)
    books.forEachIndexed { index, book ->
      saveLookup(book, lookup(book))
      if (index < books.lastIndex) delay(COVER_REQUEST_DELAY_MILLIS)
    }
    return books.size == limit
  }

  private suspend fun lookup(book: LibraryBook): KindleCoverLookupResult {
    try {
      val amazon = amazonCoverClient.lookup(book.sourceId)
      if (amazon.lookup.status == CoverLookupStatus.FOUND) return amazon
    } catch (_: IOException) {
      // Amazon 商品ページの取得・解析に失敗しても Open Library を試す。
    }

    return KindleCoverLookupResult(
      lookup = openLibraryCoverClient.lookup(book),
      provider = KindleCoverProvider.OPEN_LIBRARY,
    )
  }

  private fun queryCandidates(limit: Int): List<LibraryBook> {
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
        LIMIT ?
      """.trimIndent(),
      arrayOf(
        LibrarySource.KINDLE.name,
        staleBefore.toString(),
        limit.toString(),
      ),
    ).use { cursor ->
      val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
      val titleIndex = cursor.getColumnIndexOrThrow("title")
      val authorsIndex = cursor.getColumnIndexOrThrow("authors")
      val isbn10Index = cursor.getColumnIndexOrThrow("isbn10")
      val isbn13Index = cursor.getColumnIndexOrThrow("isbn13")
      buildList {
        while (cursor.moveToNext()) {
          add(
            LibraryBook(
              source = LibrarySource.KINDLE,
              sourceId = cursor.getString(sourceIdIndex),
              title = cursor.getString(titleIndex),
              authors = parseKindleAuthors(cursor.getString(authorsIndex)),
              publisher = null,
              publishedDate = null,
              description = null,
              isbn10 = if (cursor.isNull(isbn10Index)) null else cursor.getString(isbn10Index),
              isbn13 = if (cursor.isNull(isbn13Index)) null else cursor.getString(isbn13Index),
              thumbnailUrl = null,
              infoUrl = null,
            ),
          )
        }
      }
    }
  }

  private fun saveLookup(
    book: LibraryBook,
    result: KindleCoverLookupResult,
  ) {
    val lookup = result.lookup
    val values = ContentValues().apply {
      put("source", LibrarySource.KINDLE.name)
      put("source_id", book.sourceId)
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

  private suspend fun ensureSchema() {
    if (schemaEnsured) return
    DefaultLibraryRepository(database).snapshot()
    schemaEnsured = true
  }

  private fun isEnabled(): Boolean = database.readable.rawQuery(
    "SELECT value FROM library_settings WHERE key = ? LIMIT 1",
    arrayOf(KINDLE_COVER_ENRICHMENT_SETTING),
  ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }

  private companion object {
    const val KINDLE_COVER_BATCH_SIZE = 1
    const val COVER_REQUEST_DELAY_MILLIS = 1_100L
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val KINDLE_COVER_ENRICHMENT_SETTING = "kindle_cover_enrichment_enabled"
  }
}

private fun parseKindleAuthors(value: String): List<String> = runCatching {
  val array = JSONArray(value)
  buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}.getOrElse { emptyList() }
