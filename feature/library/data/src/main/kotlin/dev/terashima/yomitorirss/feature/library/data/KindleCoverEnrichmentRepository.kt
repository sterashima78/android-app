package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
) {
  private val amazonCoverClient = KindleAmazonCoverClient()
  private val openLibraryCoverClient = OpenLibraryCoverClient()
  private var schemaEnsured = false

  suspend fun enrichNext(): Boolean {
    ensureSchema()
    if (!isEnabled()) return false
    val book = queryCandidate() ?: return false
    saveLookup(book, lookup(book))
    return true
  }

  private suspend fun lookup(book: LibraryBook): KindleCoverLookupResult {
    var amazonFailure: IOException? = null
    val amazon = try {
      amazonCoverClient.lookup(book.sourceId)
    } catch (error: IOException) {
      amazonFailure = error
      null
    }
    if (amazon?.lookup?.status == CoverLookupStatus.FOUND) return amazon

    val openLibrary = openLibraryCoverClient.lookup(book)
    if (amazonFailure != null && openLibrary.status != CoverLookupStatus.FOUND) {
      throw amazonFailure
    }
    return KindleCoverLookupResult(
      lookup = openLibrary,
      provider = KindleCoverProvider.OPEN_LIBRARY,
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
