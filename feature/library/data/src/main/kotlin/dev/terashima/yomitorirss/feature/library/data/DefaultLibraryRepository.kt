package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySourceState
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.delay
import org.json.JSONArray

class DefaultLibraryRepository(
  private val database: DatabaseConnection,
) : LibraryRepository {
  private val googleBooks = GoogleBooksApiClient()
  private val amazonLibraryImporter = AmazonLibraryImporter()
  private val audibleMetadataEnricher = AudibleLibraryMetadataEnricher()
  private val openLibraryCoverClient = OpenLibraryCoverClient()

  override suspend fun snapshot(): LibrarySnapshot {
    ensureSchema()
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
      kindleCoverEnrichmentEnabled = isKindleCoverEnrichmentEnabled(),
    )
  }

  override suspend fun hideBook(book: LibraryBook) {
    ensureSchema()
    val values = ContentValues().apply {
      put("source", book.source.name)
      put("source_id", book.sourceId)
      put("hidden_at", System.currentTimeMillis())
    }
    database.writable.insertWithOnConflict(
      "hidden_library_items",
      null,
      values,
      SQLiteDatabase.CONFLICT_IGNORE,
    )
  }

  override suspend fun restoreBook(book: LibraryBook) {
    ensureSchema()
    database.writable.delete(
      "hidden_library_items",
      "source = ? AND source_id = ?",
      arrayOf(book.source.name, book.sourceId),
    )
  }

  override suspend fun setBookSeries(
    book: LibraryBook,
    series: LibrarySeries,
  ) {
    val seriesName = series.name.trim()
    val seriesPosition = series.position
    require(seriesName.isNotEmpty()) { "シリーズ名を入力してください" }
    require(seriesPosition == null || seriesPosition > 0) { "巻数は1以上で入力してください" }
    ensureSchema()
    val values = ContentValues().apply {
      put("source", book.source.name)
      put("source_id", book.sourceId)
      put("series_name", seriesName)
      seriesPosition?.let { put("series_position", it) } ?: putNull("series_position")
      put("updated_at", System.currentTimeMillis())
    }
    database.transaction {
      insertWithOnConflict(
        "library_item_series",
        null,
        values,
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      delete(
        "library_item_series_exclusions",
        "source = ? AND source_id = ?",
        arrayOf(book.source.name, book.sourceId),
      )
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

  override suspend fun setKindleCoverEnrichmentEnabled(enabled: Boolean) {
    ensureSchema()
    val values = ContentValues().apply {
      put("key", KINDLE_COVER_ENRICHMENT_SETTING)
      put("value", if (enabled) "1" else "0")
      put("updated_at", System.currentTimeMillis())
    }
    database.writable.insertWithOnConflict(
      "library_settings",
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
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

  override suspend fun importAmazonLibrary(
    source: LibrarySource,
    fileName: String?,
    input: InputStream,
  ): LibrarySyncResult {
    ensureSchema()
    val books = when (source) {
      LibrarySource.KINDLE -> amazonLibraryImporter.parseKindle(fileName, input)
      LibrarySource.AUDIBLE -> {
        val bytes = input.readLimited(MAX_AUDIBLE_IMPORT_BYTES)
        val parsedBooks = amazonLibraryImporter.parse(source, fileName, bytes)
        audibleMetadataEnricher.enrich(fileName, bytes, parsedBooks)
      }
      LibrarySource.GOOGLE_PLAY_BOOKS -> error("対応していない蔵書ソースです")
    }
    return replaceSource(source = source, books = books, accountLabel = null)
  }

  suspend fun enrichKindleCoverBatch(limit: Int = KINDLE_COVER_BATCH_SIZE): Boolean {
    require(limit > 0) { "表紙補完の処理件数は1件以上で指定してください" }
    ensureSchema()
    if (!isKindleCoverEnrichmentEnabled()) return false

    val books = queryKindleCoverCandidates(limit)
    books.forEachIndexed { index, book ->
      saveCoverLookup(book, openLibraryCoverClient.lookup(book))
      if (index < books.lastIndex) delay(COVER_REQUEST_DELAY_MILLIS)
    }
    return books.size == limit
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
      delete(
        "library_item_external_metadata",
        "source = ? AND source_id NOT IN (SELECT source_id FROM library_items WHERE source = ?)",
        arrayOf(source.name, source.name),
      )
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
    return LibrarySyncResult(importedCount = books.size, syncedAtEpochMillis = syncedAt)
  }

  private fun queryBooks(hidden: Boolean): List<LibraryBook> {
    val hiddenPredicate = if (hidden) "EXISTS" else "NOT EXISTS"
    return database.readable.rawQuery(
      """
        SELECT item.source, item.source_id, item.title, item.authors, item.publisher,
               item.published_date, item.description, item.isbn10, item.isbn13,
               CASE
                 WHEN item.thumbnail_url IS NOT NULL AND TRIM(item.thumbnail_url) <> ''
                   THEN item.thumbnail_url
                 ELSE metadata.thumbnail_url
               END AS thumbnail_url,
               item.info_url, item.narrators, item.duration,
               series.series_name, series.series_position,
               exclusion.source AS automatic_series_exclusion
        FROM library_items AS item
        LEFT JOIN library_item_external_metadata AS metadata
          ON metadata.source = item.source AND metadata.source_id = item.source_id
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

  private fun queryKindleCoverCandidates(limit: Int): List<LibraryBook> {
    val staleBefore = System.currentTimeMillis() - COVER_LOOKUP_STALE_MILLIS
    return database.readable.rawQuery(
      """
        SELECT item.source, item.source_id, item.title, item.authors, item.publisher,
               item.published_date, item.description, item.isbn10, item.isbn13,
               item.thumbnail_url, item.info_url, item.narrators, item.duration
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
      buildList {
        while (cursor.moveToNext()) add(cursor.toStoredBook())
      }
    }
  }

  private fun saveCoverLookup(
    book: LibraryBook,
    result: CoverLookupResult,
  ) {
    val values = ContentValues().apply {
      put("source", book.source.name)
      put("source_id", book.sourceId)
      result.thumbnailUrl?.let { put("thumbnail_url", it) } ?: putNull("thumbnail_url")
      put("provider", OPEN_LIBRARY_PROVIDER)
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

  private fun isKindleCoverEnrichmentEnabled(): Boolean {
    return database.readable.rawQuery(
      "SELECT value FROM library_settings WHERE key = ? LIMIT 1",
      arrayOf(KINDLE_COVER_ENRICHMENT_SETTING),
    ).use { cursor -> cursor.moveToFirst() && cursor.getString(0) == "1" }
  }

  private fun ensureSchema() {
    database.transaction {
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS library_items(
            source TEXT NOT NULL,
            source_id TEXT NOT NULL,
            title TEXT NOT NULL,
            authors TEXT NOT NULL,
            publisher TEXT,
            published_date TEXT,
            description TEXT,
            isbn10 TEXT,
            isbn13 TEXT,
            thumbnail_url TEXT,
            info_url TEXT,
            narrators TEXT NOT NULL DEFAULT '[]',
            duration TEXT,
            synced_at INTEGER NOT NULL,
            PRIMARY KEY(source, source_id)
          )
        """.trimIndent(),
      )
      ensureColumn("library_items", "narrators", "TEXT NOT NULL DEFAULT '[]'")
      ensureColumn("library_items", "duration", "TEXT")
      execSQL(
        "CREATE INDEX IF NOT EXISTS library_items_source_title " +
          "ON library_items(source, title COLLATE NOCASE)",
      )
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS library_sources(
            source TEXT PRIMARY KEY NOT NULL,
            account_label TEXT,
            last_synced_at INTEGER
          )
        """.trimIndent(),
      )
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS hidden_library_items(
            source TEXT NOT NULL,
            source_id TEXT NOT NULL,
            hidden_at INTEGER NOT NULL,
            PRIMARY KEY(source, source_id)
          )
        """.trimIndent(),
      )
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS library_item_series(
            source TEXT NOT NULL,
            source_id TEXT NOT NULL,
            series_name TEXT NOT NULL,
            series_position INTEGER,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(source, source_id)
          )
        """.trimIndent(),
      )
      execSQL(
        "CREATE INDEX IF NOT EXISTS library_item_series_name " +
          "ON library_item_series(series_name COLLATE NOCASE)",
      )
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS library_item_series_exclusions(
            source TEXT NOT NULL,
            source_id TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            PRIMARY KEY(source, source_id)
          )
        """.trimIndent(),
      )
      execSQL(
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
      execSQL(
        "CREATE INDEX IF NOT EXISTS library_item_external_metadata_status " +
          "ON library_item_external_metadata(source, lookup_status, updated_at)",
      )
      execSQL(
        """
          CREATE TABLE IF NOT EXISTS library_settings(
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
          )
        """.trimIndent(),
      )
    }
  }

  private fun SQLiteDatabase.ensureColumn(
    table: String,
    column: String,
    definition: String,
  ) {
    val exists = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
      val nameIndex = cursor.getColumnIndexOrThrow("name")
      var found = false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == column) {
          found = true
          break
        }
      }
      found
    }
    if (!exists) execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
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

  private fun Cursor.toStoredBook(): LibraryBook = LibraryBook(
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

  private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { "Audible インポートファイルが大きすぎます（上限 25 MB）" }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private companion object {
    const val MAX_AUDIBLE_IMPORT_BYTES = 25 * 1024 * 1024
    const val KINDLE_COVER_BATCH_SIZE = 10
    const val COVER_REQUEST_DELAY_MILLIS = 350L
    const val COVER_LOOKUP_STALE_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val KINDLE_COVER_ENRICHMENT_SETTING = "kindle_cover_enrichment_enabled"
    const val OPEN_LIBRARY_PROVIDER = "OPEN_LIBRARY"
  }
}
