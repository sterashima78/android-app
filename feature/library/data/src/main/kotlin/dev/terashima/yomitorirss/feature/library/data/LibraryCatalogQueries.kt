package dev.terashima.yomitorirss.feature.library.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.json.JSONArray

internal fun findLibraryBook(
  database: DatabaseConnection,
  source: LibrarySource,
  sourceId: String,
): LibraryBook? {
  ensureLibraryCatalogSchema(database.writable)
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
      WHERE item.source = ? AND item.source_id = ?
      LIMIT 1
    """.trimIndent(),
    arrayOf(source.name, sourceId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) null else cursor.toLibraryBook()
  }
}

private fun Cursor.toLibraryBook(): LibraryBook = LibraryBook(
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

private fun Cursor.nullableInt(name: String): Int? {
  val index = getColumnIndexOrThrow(name)
  return if (isNull(index)) null else getInt(index)
}
