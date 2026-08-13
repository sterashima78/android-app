package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySeriesImportSupport
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

internal data class KindleSeriesMetadata(
  val seriesId: String,
  val series: LibrarySeries,
)

internal object KindleSeriesMetadataParser {
  fun matches(path: String): Boolean =
    path.substringAfterLast('/').substringAfterLast('\\').equals(
      SERIES_METADATA_FILE_NAME,
      ignoreCase = true,
    )

  fun parse(bytes: ByteArray): Map<String, KindleSeriesMetadata> {
    val text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    val rows = parseRows(text).filterNot { row -> row.all(String::isBlank) }
    if (rows.size < 2) return emptyMap()

    val header = rows.first().map(::normalizeHeader)
    val seriesIdIndex = header.indexOf("seriesasin")
    val seriesNameIndex = header.indexOf("seriesproductname")
    val itemIdIndex = header.indexOf("itemasin")
    val itemPositionIndex = header.indexOf("itempositioninseries")
    if (
      seriesIdIndex < 0 ||
      seriesNameIndex < 0 ||
      itemIdIndex < 0 ||
      itemPositionIndex < 0
    ) {
      return emptyMap()
    }

    return buildMap {
      rows.drop(1).forEach { row ->
        val seriesId = row.valueAt(seriesIdIndex).amazonValue() ?: return@forEach
        val seriesName = row.valueAt(seriesNameIndex).amazonValue() ?: return@forEach
        val itemId = row.valueAt(itemIdIndex).amazonValue()?.uppercase(Locale.ROOT)
          ?: return@forEach
        val zeroBasedPosition = row.valueAt(itemPositionIndex)
          .amazonValue()
          ?.toIntOrNull()
          ?.takeIf { it >= 0 && it < Int.MAX_VALUE }
          ?: return@forEach

        put(
          itemId,
          KindleSeriesMetadata(
            seriesId = seriesId,
            series = LibrarySeries(
              name = seriesName,
              position = zeroBasedPosition + 1,
            ),
          ),
        )
      }
    }
  }

  private fun parseRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0

    fun finishField() {
      row += field.toString()
      field.setLength(0)
    }

    fun finishRow() {
      finishField()
      rows += row
      row = mutableListOf()
    }

    while (index < text.length) {
      val char = text[index]
      when {
        char == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
          field.append('"')
          index += 1
        }
        char == '"' -> quoted = !quoted
        char == ',' && !quoted -> finishField()
        (char == '\n' || char == '\r') && !quoted -> {
          if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index += 1
          finishRow()
        }
        else -> field.append(char)
      }
      index += 1
    }
    if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
    return rows
  }

  private fun List<String>.valueAt(index: Int): String? = getOrNull(index)

  private fun String?.amazonValue(): String? = this
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.takeUnless { it.equals("Not Applicable", ignoreCase = true) }
    ?.takeUnless { it.equals("Not Available", ignoreCase = true) }

  private fun normalizeHeader(value: String): String = value
    .removePrefix("\uFEFF")
    .trim()
    .lowercase(Locale.ROOT)
    .filter(Char::isLetterOrDigit)

  private const val SERIES_METADATA_FILE_NAME =
    "Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv"
}

internal class KindleSeriesMetadataScanner {
  fun scan(
    fileName: String?,
    input: InputStream,
  ): Map<String, KindleSeriesMetadata> {
    val buffered = input.asBufferedInputStream()
    if (!isZip(fileName, buffered)) return emptyMap()

    val state = ScanState()
    scanZip(buffered, depth = 0, state = state)
    return state.seriesByItemAsin
  }

  private fun scanZip(
    input: InputStream,
    depth: Int,
    state: ScanState,
  ) {
    require(depth <= MAX_NESTED_ZIP_DEPTH) {
      "ZIP の入れ子が深すぎます（上限 $MAX_NESTED_ZIP_DEPTH 階層）"
    }

    ZipInputStream(NonClosingInputStream(input)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        state.entryCount += 1
        require(state.entryCount <= MAX_ZIP_ENTRIES) {
          "ZIP 内のファイル数が多すぎます（上限 $MAX_ZIP_ENTRIES 件）"
        }

        when {
          entry.isDirectory -> Unit
          KindleSeriesMetadataParser.matches(entry.name) -> {
            val remaining = MAX_TOTAL_SERIES_BYTES - state.expandedSeriesBytes
            require(remaining > 0) {
              "Kindle シリーズ CSV の合計サイズが大きすぎます（上限 50 MB）"
            }
            val entryLimit = minOf(MAX_SERIES_ENTRY_BYTES.toLong(), remaining).toInt()
            val bytes = zip.readLimited(
              limit = entryLimit,
              tooLargeMessage = if (remaining < MAX_SERIES_ENTRY_BYTES) {
                "Kindle シリーズ CSV の合計サイズが大きすぎます（上限 50 MB）"
              } else {
                "Kindle シリーズ CSV が大きすぎます（1ファイル上限 25 MB）"
              },
            )
            state.expandedSeriesBytes += bytes.size
            state.seriesByItemAsin.putAll(KindleSeriesMetadataParser.parse(bytes))
          }
          entry.name.endsWith(".zip", ignoreCase = true) -> {
            scanZip(zip, depth = depth + 1, state = state)
          }
        }
        zip.closeEntry()
      }
    }
  }

  private fun isZip(
    fileName: String?,
    input: BufferedInputStream,
  ): Boolean {
    if (fileName?.endsWith(".zip", ignoreCase = true) == true) return true

    input.mark(ZIP_MAGIC.size)
    val magic = ByteArray(ZIP_MAGIC.size)
    var offset = 0
    while (offset < magic.size) {
      val read = input.read(magic, offset, magic.size - offset)
      if (read < 0) break
      offset += read
    }
    input.reset()
    return offset == magic.size && magic.contentEquals(ZIP_MAGIC)
  }

  private fun InputStream.asBufferedInputStream(): BufferedInputStream =
    this as? BufferedInputStream ?: BufferedInputStream(this)

  private fun InputStream.readLimited(
    limit: Int,
    tooLargeMessage: String,
  ): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { tooLargeMessage }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
  }

  private data class ScanState(
    val seriesByItemAsin: MutableMap<String, KindleSeriesMetadata> = linkedMapOf(),
    var entryCount: Int = 0,
    var expandedSeriesBytes: Long = 0L,
  )

  private companion object {
    val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    const val MAX_NESTED_ZIP_DEPTH = 4
    const val MAX_ZIP_ENTRIES = 100_000
    const val MAX_SERIES_ENTRY_BYTES = 25 * 1024 * 1024
    const val MAX_TOTAL_SERIES_BYTES = 50L * 1024 * 1024
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
    metadata?.let { book.copy(series = it.series) } ?: book
  }
}

internal fun String.normalizeAmazonSourceId(): String = trim().uppercase(Locale.ROOT)

internal class KindleSourceSeriesRepository(
  private val database: DatabaseConnection,
  private val scanner: KindleSeriesMetadataScanner = KindleSeriesMetadataScanner(),
) {
  fun importMetadata(
    fileName: String?,
    input: InputStream,
  ) {
    val scanned = scanner.scan(fileName, input)
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
          put("series_name", metadata.series.name)
          metadata.series.position?.let { put("series_position", it) } ?: putNull("series_position")
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
        put(
          cursor.getString(sourceIdIndex).normalizeAmazonSourceId(),
          KindleSeriesMetadata(
            seriesId = cursor.getString(seriesIdIndex),
            series = LibrarySeries(
              name = cursor.getString(seriesNameIndex),
              position = position,
            ),
          ),
        )
      }
    }
  }

  private fun ensureSchema() {
    database.writable.execSQL(
      """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME(
          source TEXT NOT NULL,
          source_id TEXT NOT NULL,
          series_id TEXT NOT NULL,
          series_name TEXT NOT NULL,
          series_position INTEGER,
          updated_at INTEGER NOT NULL,
          PRIMARY KEY(source, source_id)
        )
      """.trimIndent(),
    )
    database.writable.execSQL(
      "CREATE INDEX IF NOT EXISTS library_source_series_name " +
        "ON $TABLE_NAME(source, series_name COLLATE NOCASE)",
    )
  }

  private companion object {
    const val TABLE_NAME = "library_source_series"
  }
}

class SeriesAwareLibraryRepository private constructor(
  private val delegate: LibraryRepository,
  private val sourceSeriesRepository: KindleSourceSeriesRepository,
) : LibraryRepository by delegate, LibrarySeriesImportSupport {
  constructor(database: DatabaseConnection) : this(
    delegate = DefaultLibraryRepository(database),
    sourceSeriesRepository = KindleSourceSeriesRepository(database),
  )

  override suspend fun snapshot(): LibrarySnapshot = sourceSeriesRepository.enrich(delegate.snapshot())

  override suspend fun importSeriesMetadata(
    source: LibrarySource,
    fileName: String?,
    input: InputStream,
  ) {
    require(source == LibrarySource.KINDLE) { "Kindle のシリーズ情報のみインポートできます" }
    sourceSeriesRepository.importMetadata(fileName, input)
  }

  override suspend fun clearSeriesMetadata(source: LibrarySource) {
    require(source == LibrarySource.KINDLE) { "Kindle のシリーズ情報のみ削除できます" }
    sourceSeriesRepository.clear()
  }
}
