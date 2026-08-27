package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookSeriesUpdate
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySourceState
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.library.isKindlePersonalDocument
import org.json.JSONArray

class DefaultLibraryRepository(
  private val database: DatabaseConnection,
) : LibraryRepository {
  private val googleBooks = GoogleBooksApiClient()
  private val kindleWebLibraryImporter = KindleWebLibraryImporter()
  private val audibleWebLibraryImporter = AudibleWebLibraryImporter()

  override suspend fun snapshot(): LibrarySnapshot {
    ensureSchema()
    normalizeStoredKindleBookTitles()
    val books = queryBooks(hidden = false)
    val hiddenBooks = queryBooks(hidden = true)
    val sourceStates = database.readable.rawQuery(
      "SELECT source, account_label, last_synced_at FROM library_sources",
      null,
    ).use { cursor ->
      buildMap {
        while (cursor.moveToNext()) {
          val source = LibrarySource.valueOf(cursor.string("source"))
          put(
            source,
            LibrarySourceState(
              source = source,
              accountLabel = cursor.nullableString("account_label"),
              lastSyncedAtEpochMillis = cursor.nullableLong("last_synced_at"),
            ),
          )
        }
      }
    }
    return LibrarySnapshot(
      books = books,
      hiddenBooks = hiddenBooks,
      sourceStates = sourceStates,
    )
  }

  override suspend fun hideBook(book: LibraryBook) {
    ensureSchema()
    val values = ContentValues().apply {
      put("source", book.source.name)
      put("source_id", book.sourceId)
      put("hidden_at", System.currentTimeMillis())
    }
    database.write {
      insertWithOnConflict(
        "hidden_library_items",
        null,
        values,
        SQLiteDatabase.CONFLICT_IGNORE,
      )
    }
  }

  override suspend fun restoreBook(book: LibraryBook) {
    ensureSchema()
    database.write {
      delete(
        "hidden_library_items",
        "source = ? AND source_id = ?",
        arrayOf(book.source.name, book.sourceId),
      )
    }
  }

  override suspend fun setBookSeries(
    book: LibraryBook,
    series: LibrarySeries,
  ) {
    setBookSeries(listOf(LibraryBookSeriesUpdate(book, series)))
  }

  override suspend fun setBookSeries(updates: List<LibraryBookSeriesUpdate>) {
    if (updates.isEmpty()) return
    val normalizedUpdates = updates.map { update ->
      val seriesName = update.series.name.trim()
      val seriesPosition = update.series.position
      require(seriesName.isNotEmpty()) { "シリーズ名を入力してください" }
      require(seriesPosition == null || seriesPosition > 0) { "巻数は1以上で入力してください" }
      update.copy(series = update.series.copy(name = seriesName, id = null))
    }
    ensureSchema()
    val updatedAt = System.currentTimeMillis()
    database.transaction {
      normalizedUpdates.forEach { update ->
        val values = ContentValues().apply {
          put("source", update.book.source.name)
          put("source_id", update.book.sourceId)
          put("series_name", update.series.name)
          update.series.position?.let { put("series_position", it) } ?: putNull("series_position")
          put("updated_at", updatedAt)
        }
        insertWithOnConflict(
          "library_item_series",
          null,
          values,
          SQLiteDatabase.CONFLICT_REPLACE,
        )
        delete(
          "library_item_series_exclusions",
          "source = ? AND source_id = ?",
          arrayOf(update.book.source.name, update.book.sourceId),
        )
      }
    }
  }

  override suspend fun clearBookSeries(book: LibraryBook) {
    ensureSchema()
    val exclusionValues = ContentValues().apply {
      put("source", book.source.name)
      put("source_id", book.sourceId)
      put("updated_at", System.currentTimeMillis())
    }
    database.transaction {
      delete(
        "library_item_series",
        "source = ? AND source_id = ?",
        arrayOf(book.source.name, book.sourceId),
      )
      insertWithOnConflict(
        "library_item_series_exclusions",
        null,
        exclusionValues,
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  override suspend fun syncGooglePlayBooks(
    accessToken: String,
    accountLabel: String?,
  ): LibrarySyncResult {
    ensureSchema()
    val books = googleBooks.library(accessToken)
    return replaceSource(
      source = LibrarySource.GOOGLE_PLAY_BOOKS,
      books = books,
      accountLabel = accountLabel,
    )
  }

  override suspend fun importAmazonLibraryJson(
    source: LibrarySource,
    json: String,
  ): LibrarySyncResult {
    ensureSchema()
    return when (source) {
      LibrarySource.KINDLE -> replaceKindleItems(kindleWebLibraryImporter.parse(json))
      LibrarySource.AUDIBLE -> replaceSource(
        source = source,
        books = audibleWebLibraryImporter.parse(json),
        accountLabel = null,
      )
      LibrarySource.GOOGLE_PLAY_BOOKS,
      LibrarySource.SMB,
      LibrarySource.WEB,
      -> error("対応していない蔵書ソースです")
    }
  }

  private fun replaceKindleItems(books: List<LibraryBook>): LibrarySyncResult {
    val personalDocuments = books.filter { it.isKindlePersonalDocument() }
    require(personalDocuments.isEmpty() || personalDocuments.size == books.size) {
      "Kindle 通常本と Personal Document が混在したインポートはできません"
    }
    val isPersonalDocumentImport = personalDocuments.isNotEmpty()
    val normalizedBooks = books.map { book ->
      if (book.isKindlePersonalDocument()) {
        book
      } else {
        book.copy(title = normalizeKindleBookTitle(book.title))
      }
    }
    val syncedAt = System.currentTimeMillis()
    database.transaction {
      val prefixPattern = "$KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX%"
      val where = if (isPersonalDocumentImport) {
        "source = ? AND source_id LIKE ?"
      } else {
        "source = ? AND source_id NOT LIKE ?"
      }
      delete("library_items", where, arrayOf(LibrarySource.KINDLE.name, prefixPattern))
      normalizedBooks.forEach { book ->
        insertOrThrow("library_items", null, book.toValues(syncedAt))
      }
      updateSourceState(LibrarySource.KINDLE, accountLabel = null, syncedAt = syncedAt)
    }
    return LibrarySyncResult(importedCount = books.size, syncedAtEpochMillis = syncedAt)
  }

  private fun replaceSource(
    source: LibrarySource,
    books: List<LibraryBook>,
    accountLabel: String?,
  ): LibrarySyncResult {
    val syncedAt = System.currentTimeMillis()
    database.transaction {
      delete("library_items", "source = ?", arrayOf(source.name))
      books.forEach { book ->
        insertOrThrow("library_items", null, book.toValues(syncedAt))
      }
      updateSourceState(source, accountLabel, syncedAt)
    }
    return LibrarySyncResult(importedCount = books.size, syncedAtEpochMillis = syncedAt)
  }

  private fun SQLiteDatabase.updateSourceState(
    source: LibrarySource,
    accountLabel: String?,
    syncedAt: Long,
  ) {
    val sourceValues = ContentValues().apply {
      put("source", source.name)
      accountLabel?.let { put("account_label", it) } ?: putNull("account_label")
      put("last_synced_at", syncedAt)
    }
    insertWithOnConflict(
      "library_sources",
      null,
      sourceValues,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  private fun queryBooks(hidden: Boolean): List<LibraryBook> {
    val hiddenPredicate = if (hidden) "EXISTS" else "NOT EXISTS"
    return database.readable.rawQuery(
      """
        SELECT item.source, item.source_id, item.title, item.authors, item.publisher,
               item.published_date, item.description, item.isbn10, item.isbn13,
               item.thumbnail_url, item.info_url, item.narrators, item.duration,
               series.series_name, series.series_position,
               exclusion.source AS automatic_series_exclusion
        FROM library_items AS item
        LEFT JOIN library_item_series AS series
          ON series.source = item.source AND series.source_id = item.source_id
        LEFT JOIN library_item_series_exclusions AS exclusion
          ON exclusion.source = item.source AND exclusion.source_id = item.source_id
        WHERE $hiddenPredicate (
          SELECT 1
          FROM hidden_library_items AS hidden
          WHERE hidden.source = item.source AND hidden.source_id = item.source_id
        )
        ORDER BY item.title COLLATE NOCASE, item.source_id
      """.trimIndent(),
      null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toBook()) } }
  }

  private fun ensureSchema() {
    ensureLibraryCatalogSchema(database.writable)
  }

  private fun normalizeStoredKindleBookTitles() {
    val updates = database.readable.rawQuery(
      """
        SELECT source_id, title
        FROM library_items
        WHERE source = ?
          AND source_id NOT LIKE ?
          AND title LIKE ?
      """.trimIndent(),
      arrayOf(
        LibrarySource.KINDLE.name,
        "$KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX%",
        "%$KINDLE_JAPANESE_EDITION_SUFFIX%",
      ),
    ).use { cursor ->
      buildList {
        val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
        val titleIndex = cursor.getColumnIndexOrThrow("title")
        while (cursor.moveToNext()) {
          val sourceId = cursor.getString(sourceIdIndex)
          val title = cursor.getString(titleIndex)
          val normalizedTitle = normalizeKindleBookTitle(title)
          if (normalizedTitle != title) add(sourceId to normalizedTitle)
        }
      }
    }
    if (updates.isEmpty()) return

    database.transaction {
      updates.forEach { (sourceId, title) ->
        update(
          "library_items",
          ContentValues().apply { put("title", title) },
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.KINDLE.name, sourceId),
        )
      }
    }
  }

  private fun LibraryBook.toValues(syncedAt: Long): ContentValues = ContentValues().apply {
    put("source", source.name)
    put("source_id", sourceId)
    put("title", title)
    put("authors", JSONArray(authors).toString())
    put("publisher", publisher)
    put("published_date", publishedDate)
    put("description", description)
    put("isbn10", isbn10)
    put("isbn13", isbn13)
    put("thumbnail_url", thumbnailUrl)
    put("info_url", infoUrl)
    put("narrators", JSONArray(narrators).toString())
    put("duration", duration)
    put("synced_at", syncedAt)
  }

  private fun Cursor.toBook(): LibraryBook = LibraryBook(
    source = LibrarySource.valueOf(string("source")),
    sourceId = string("source_id"),
    title = string("title"),
    authors = jsonStringList("authors"),
    publisher = nullableString("publisher"),
    publishedDate = nullableString("published_date"),
    description = nullableString("description"),
    isbn10 = nullableString("isbn10"),
    isbn13 = nullableString("isbn13"),
    thumbnailUrl = nullableString("thumbnail_url"),
    infoUrl = nullableString("info_url"),
    series = nullableString("series_name")?.let { name ->
      LibrarySeries(
        name = name,
        position = nullableInt("series_position"),
      )
    },
    automaticSeriesExcluded = nullableString("automatic_series_exclusion") != null,
    narrators = jsonStringList("narrators"),
    duration = nullableString("duration"),
  )

  private fun Cursor.jsonStringList(name: String): List<String> =
    JSONArray(string(name)).let { array ->
      buildList { for (index in 0 until array.length()) add(array.optString(index)) }
        .filter(String::isNotBlank)
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

  private fun Cursor.nullableInt(name: String): Int? {
    val index = getColumnIndexOrThrow(name)
    return if (isNull(index)) null else getInt(index)
  }
}
