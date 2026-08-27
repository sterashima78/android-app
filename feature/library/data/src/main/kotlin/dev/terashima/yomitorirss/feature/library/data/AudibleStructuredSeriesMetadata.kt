package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource

internal data class AudibleSeriesMetadata(
  val seriesId: String?,
  val seriesName: String?,
  val position: Int?,
)

internal class AudibleSeriesMetadataScanner {
  fun scan(json: String): Map<String, AudibleSeriesMetadata> =
    AudibleWebLibraryExportParser.parse(json).seriesBySourceId
}

internal fun List<LibraryBook>.applyAudibleSeries(
  seriesBySourceId: Map<String, AudibleSeriesMetadata>,
): List<LibraryBook> = map { book ->
  if (
    book.source != LibrarySource.AUDIBLE ||
    book.series != null ||
    book.automaticSeriesExcluded
  ) {
    book
  } else {
    val metadata = seriesBySourceId[book.sourceId.normalizeAmazonSourceId()]
    metadata?.let { book.copy(series = it.toLibrarySeries()) } ?: book
  }
}

internal class AudibleSourceSeriesRepository(
  private val database: DatabaseConnection,
  private val scanner: AudibleSeriesMetadataScanner = AudibleSeriesMetadataScanner(),
) {
  fun importMetadata(json: String) {
    replace(scanner.scan(json))
  }

  fun clear() {
    ensureSchema()
    database.write { delete(TABLE_NAME, null, null) }
  }

  fun enrich(snapshot: LibrarySnapshot): LibrarySnapshot {
    ensureSchema()
    val seriesBySourceId = querySeries()
    if (seriesBySourceId.isEmpty()) return snapshot
    return snapshot.copy(
      books = snapshot.books.applyAudibleSeries(seriesBySourceId),
      hiddenBooks = snapshot.hiddenBooks.applyAudibleSeries(seriesBySourceId),
    )
  }

  private fun replace(seriesBySourceId: Map<String, AudibleSeriesMetadata>) {
    ensureSchema()
    val ownedSourceIds = queryOwnedSourceIds()
    val updatedAt = System.currentTimeMillis()
    database.transaction {
      delete(TABLE_NAME, null, null)
      seriesBySourceId.forEach { (sourceId, metadata) ->
        val normalizedSourceId = sourceId.normalizeAmazonSourceId()
        if (normalizedSourceId !in ownedSourceIds) return@forEach
        val values = ContentValues().apply {
          put("source_id", normalizedSourceId)
          metadata.seriesId?.let { put("series_id", it) } ?: putNull("series_id")
          put("series_name", metadata.seriesName.orEmpty())
          metadata.position?.let { put("series_position", it) } ?: putNull("series_position")
          put("updated_at", updatedAt)
        }
        insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
    }
  }

  private fun queryOwnedSourceIds(): Set<String> = database.readable.rawQuery(
    "SELECT source_id FROM library_items WHERE source = ?",
    arrayOf(LibrarySource.AUDIBLE.name),
  ).use { cursor ->
    buildSet {
      val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
      while (cursor.moveToNext()) add(cursor.getString(sourceIdIndex).normalizeAmazonSourceId())
    }
  }

  private fun querySeries(): Map<String, AudibleSeriesMetadata> = database.readable.rawQuery(
    """
      SELECT source_id, series_id, series_name, series_position
      FROM $TABLE_NAME
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildMap {
      val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
      val seriesIdIndex = cursor.getColumnIndexOrThrow("series_id")
      val seriesNameIndex = cursor.getColumnIndexOrThrow("series_name")
      val seriesPositionIndex = cursor.getColumnIndexOrThrow("series_position")
      while (cursor.moveToNext()) {
        val id = if (cursor.isNull(seriesIdIndex)) null else cursor.getString(seriesIdIndex)
        val name = cursor.getString(seriesNameIndex).trim().takeIf(String::isNotEmpty)
        val position = if (cursor.isNull(seriesPositionIndex)) null else cursor.getInt(seriesPositionIndex)
        put(
          cursor.getString(sourceIdIndex).normalizeAmazonSourceId(),
          AudibleSeriesMetadata(
            seriesId = id,
            seriesName = name,
            position = position,
          ),
        )
      }
    }
  }

  private fun ensureSchema() {
    ensureLibraryStructuredSeriesSchema(database.writable)
  }

  private companion object {
    const val TABLE_NAME = "library_audible_source_series"
  }
}
