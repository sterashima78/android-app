package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class KindleWebLibraryExport(
  val books: List<LibraryBook>,
  val seriesBySourceId: Map<String, KindleSeriesMetadata>,
)

internal class KindleWebLibraryImporter {
  fun parse(
    fileName: String?,
    input: InputStream,
  ): List<LibraryBook> = KindleWebLibraryExportParser.parse(fileName, input).books
}

internal object KindleWebLibraryExportParser {
  fun parse(
    fileName: String?,
    input: InputStream,
  ): KindleWebLibraryExport {
    if (!fileName.isNullOrBlank()) {
      require(fileName.endsWith(".json", ignoreCase = true)) {
        "Kindle Web Library から保存した JSON ファイルを選択してください"
      }
    }

    val bytes = input.readLimited(MAX_INPUT_BYTES)
    require(bytes.isNotEmpty()) { "インポートファイルが空です" }
    val root = runCatching {
      JSONObject(bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF"))
    }.getOrElse {
      throw IllegalArgumentException("Kindle Web Library の JSON を解析できませんでした", it)
    }

    require(root.optString("format") == EXPORT_FORMAT) {
      "Kindle Web Library のエクスポート JSON ではありません"
    }
    require(root.optInt("version", -1) == EXPORT_VERSION) {
      "対応していない Kindle Web Library エクスポート形式です"
    }
    val bookValues = root.optJSONArray("books")
      ?: throw IllegalArgumentException("Kindle Web Library の books が見つかりません")

    val booksByAsin = linkedMapOf<String, LibraryBook>()
    val seriesBySourceId = linkedMapOf<String, KindleSeriesMetadata>()
    for (index in 0 until bookValues.length()) {
      val value = bookValues.optJSONObject(index)
        ?: throw IllegalArgumentException("Kindle Web Library の books[$index] が不正です")
      val asin = value.requiredAsin(index)
      val title = value.optString("title").trim()
      require(title.isNotEmpty()) { "Kindle Web Library の books[$index] にタイトルがありません" }

      val authors = value.optJSONArray("authors").stringList()
      val coverUrl = value.nullableString("coverUrl")
      val importedSeries = value.series(index)
      val series = importedSeries?.toLibrarySeries()
      if (importedSeries != null) {
        seriesBySourceId[asin] = importedSeries
      } else {
        seriesBySourceId.remove(asin)
      }

      booksByAsin[asin] = LibraryBook(
        source = LibrarySource.KINDLE,
        sourceId = asin,
        title = title,
        authors = authors,
        publisher = null,
        publishedDate = null,
        description = null,
        isbn10 = null,
        isbn13 = null,
        thumbnailUrl = coverUrl,
        infoUrl = null,
        series = series,
      )
    }

    require(booksByAsin.isNotEmpty()) { "Kindle Web Library の蔵書が見つかりませんでした" }
    return KindleWebLibraryExport(
      books = booksByAsin.values.toList(),
      seriesBySourceId = seriesBySourceId,
    )
  }

  private fun JSONObject.requiredAsin(index: Int): String {
    val asin = optString("asin").trim().uppercase(Locale.ROOT)
    require(AMAZON_ASIN.matches(asin)) { "Kindle Web Library の books[$index] の ASIN が不正です" }
    return asin
  }

  private fun JSONObject.series(index: Int): KindleSeriesMetadata? {
    if (!has("series") || isNull("series")) return null
    val series = optJSONObject("series")
      ?: throw IllegalArgumentException("Kindle Web Library の books[$index].series が不正です")
    val id = series.optString("id").trim().uppercase(Locale.ROOT)
    require(AMAZON_ASIN.matches(id)) { "Kindle Web Library の books[$index].series.id が不正です" }
    val name = series.nullableString("name")
    val position = if (series.has("position") && !series.isNull("position")) {
      val value = series.optInt("position", Int.MIN_VALUE)
      require(value > 0) { "Kindle Web Library の books[$index].series.position が不正です" }
      value
    } else {
      null
    }
    return KindleSeriesMetadata(
      seriesId = id,
      seriesName = name,
      position = position,
    )
  }

  private fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf(String::isNotEmpty)
  }

  private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
      for (index in 0 until length()) {
        val value = opt(index)
        if (value == null || value == JSONObject.NULL) continue
        value.toString().trim().takeIf(String::isNotEmpty)?.let(::add)
      }
    }.distinctBy { it.lowercase(Locale.ROOT) }
  }

  private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { "Kindle Web Library の JSON が大きすぎます（上限 25 MB）" }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private val AMAZON_ASIN = Regex("^[A-Z0-9]{10}$")
  private const val EXPORT_FORMAT = "kindle-library-export"
  private const val EXPORT_VERSION = 1
  private const val MAX_INPUT_BYTES = 25 * 1024 * 1024
}

internal fun KindleSeriesMetadata.toLibrarySeries(): LibrarySeries = LibrarySeries(
  name = seriesName ?: "シリーズ名未取得 ($seriesId)",
  position = position,
  id = seriesId,
)
