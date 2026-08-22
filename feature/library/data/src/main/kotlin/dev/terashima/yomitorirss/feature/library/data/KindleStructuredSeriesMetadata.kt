package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeriesImportSupport
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.util.Locale

internal data class KindleSeriesMetadata(
  val seriesId: String,
  val seriesName: String?,
  val position: Int?,
)

internal class KindleSeriesMetadataScanner {
  fun scan(json: String): Map<String, KindleSeriesMetadata>? {
    val export = KindleWebLibraryExportParser.parse(json)
    return if (export.isPersonalDocumentExport) null else export.seriesBySourceId
  }
}

internal fun List<LibraryBook>.applyKindleSeries(
  seriesBySourceId: Map<String, KindleSeriesMetadata>,
): List<LibraryBook> = map { book ->
  if (
    book.source != LibrarySource.KINDLE ||
    book.series != null ||
    book.automaticSeriesExcluded
  ) {
    book
  } else {
    val metadata = seriesBySourceId[book.sourceId.normalizeAmazonSourceId()]
    metadata?.let { book.copy(series = it.toLibrarySeries()) } ?: book
  }
}

internal fun String.normalizeAmazonSourceId(): String = trim().uppercase(Locale.ROOT)

internal class KindleSourceSeriesRepository(
  private val database: DatabaseConnection,
  private val scanner: KindleSeriesMetadataScanner = KindleSeriesMetadataScanner(),
) {
  fun importMetadata(json: String) {
    val scanned = scanner.scan(json) ?: return
    replace(scanned)
  }

  fun clear() {
    ensureSchema()
    database.writable.delete(
      TABLE_NAME,
      "source = ?",
      arrayOf(LibrarySource.KINDLE.name),
    )
  }

  fun enrich(snapshot: LibrarySnapshot): LibrarySnapshot {
    ensureSchema()
    val seriesBySourceId = querySeries()
    if (seriesBySourceId.isEmpty()) return snapshot
    return snapshot.copy(
      books = snapshot.books.applyKindleSeries(seriesBySourceId),
      hiddenBooks = snapshot.hiddenBooks.applyKindleSeries(seriesBySourceId),
    )
  }

  private fun replace(seriesBySourceId: Map<String, KindleSeriesMetadata>) {
    ensureSchema()
    val ownedSourceIds = queryOwnedSourceIds()
    val updatedAt = System.currentTimeMillis()
    database.transaction {
      delete(TABLE_NAME, "source = ?", arrayOf(LibrarySource.KINDLE.name))
      seriesBySourceId.forEach { (sourceId, metadata) ->
        val normalizedSourceId = sourceId.normalizeAmazonSourceId()
        if (normalizedSourceId !in ownedSourceIds) return@forEach
        val values = ContentValues().apply {
          put("source", LibrarySource.KINDLE.name)
          put("source_id", normalizedSourceId)
          put("series_id", metadata.seriesId)
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
    arrayOf(LibrarySource.KINDLE.name),
  ).use { cursor ->
    buildSet {
      val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
      while (cursor.moveToNext()) add(cursor.getString(sourceIdIndex).normalizeAmazonSourceId())
    }
  }

  private fun querySeries(): Map<String, KindleSeriesMetadata> = database.readable.rawQuery(
    """
      SELECT source_id, series_id, series_name, series_position
      FROM $TABLE_NAME
      WHERE source = ?
    """.trimIndent(),
    arrayOf(LibrarySource.KINDLE.name),
  ).use { cursor ->
    buildMap {
      val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
      val seriesIdIndex = cursor.getColumnIndexOrThrow("series_id")
      val seriesNameIndex = cursor.getColumnIndexOrThrow("series_name")
      val seriesPositionIndex = cursor.getColumnIndexOrThrow("series_position")
      while (cursor.moveToNext()) {
        val position = if (cursor.isNull(seriesPositionIndex)) {
          null
        } else {
          cursor.getInt(seriesPositionIndex)
        }
        val rawName = cursor.getString(seriesNameIndex).trim().takeIf(String::isNotEmpty)
        put(
          cursor.getString(sourceIdIndex).normalizeAmazonSourceId(),
          KindleSeriesMetadata(
            seriesId = cursor.getString(seriesIdIndex),
            seriesName = rawName,
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
    const val TABLE_NAME = "library_source_series"
  }
}

class SeriesAwareLibraryRepository private constructor(
  private val delegate: LibraryRepository,
  private val kindleSourceSeriesRepository: KindleSourceSeriesRepository,
  private val audibleSourceSeriesRepository: AudibleSourceSeriesRepository,
) : LibraryRepository by delegate, LibrarySeriesImportSupport {
  constructor(database: DatabaseConnection) : this(
    delegate = DefaultLibraryRepository(database),
    kindleSourceSeriesRepository = KindleSourceSeriesRepository(database),
    audibleSourceSeriesRepository = AudibleSourceSeriesRepository(database),
  )

  override suspend fun snapshot(): LibrarySnapshot = audibleSourceSeriesRepository.enrich(
    kindleSourceSeriesRepository.enrich(delegate.snapshot()),
  )

  override suspend fun importSeriesMetadataJson(
    source: LibrarySource,
    json: String,
  ) {
    when (source) {
      LibrarySource.KINDLE -> kindleSourceSeriesRepository.importMetadata(json)
      LibrarySource.AUDIBLE -> audibleSourceSeriesRepository.importMetadata(json)
      LibrarySource.GOOGLE_PLAY_BOOKS,
      LibrarySource.SMB,
      LibrarySource.WEB,
      -> error("対応していない蔵書ソースです")
    }
  }

  override suspend fun clearSeriesMetadata(source: LibrarySource) {
    when (source) {
      LibrarySource.KINDLE -> kindleSourceSeriesRepository.clear()
      LibrarySource.AUDIBLE -> audibleSourceSeriesRepository.clear()
      LibrarySource.GOOGLE_PLAY_BOOKS,
      LibrarySource.SMB,
      LibrarySource.WEB,
      -> error("対応していない蔵書ソースです")
    }
  }
}
