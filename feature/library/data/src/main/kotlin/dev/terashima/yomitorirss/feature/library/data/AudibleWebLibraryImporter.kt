package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class AudibleWebLibraryExport(
  val books: List<LibraryBook>,
  val seriesBySourceId: Map<String, AudibleSeriesMetadata>,
)

internal class AudibleWebLibraryImporter {
  fun parse(
    fileName: String?,
    input: InputStream,
  ): List<LibraryBook> = AudibleWebLibraryExportParser.parse(fileName, input).books
}

internal object AudibleWebLibraryExportParser {
  fun parse(
    fileName: String?,
    input: InputStream,
  ): AudibleWebLibraryExport {
    if (!fileName.isNullOrBlank()) {
      require(fileName.isAudibleWebLibraryJson()) {
        "Audible Web Library から保存した JSON ファイルを選択してください"
      }
    }

    val bytes = input.readLimited(MAX_INPUT_BYTES)
    require(bytes.isNotEmpty()) { "インポートファイルが空です" }
    val root = runCatching {
      JSONTokener(bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")).nextValue()
    }.getOrElse {
      throw IllegalArgumentException("Audible Web Library の JSON を解析できませんでした", it)
    }

    val bookValues = when (root) {
      is JSONArray -> root
      is JSONObject -> {
        require(root.optString("format") == EXPORT_FORMAT) {
          "Audible Web Library のエクスポート JSON ではありません"
        }
        require(root.optInt("version", -1) == EXPORT_VERSION) {
          "対応していない Audible Web Library エクスポート形式です"
        }
        root.optJSONArray("books")
          ?: throw IllegalArgumentException("Audible Web Library の books が見つかりません")
      }
      else -> throw IllegalArgumentException("Audible Web Library の JSON を解析できませんでした")
    }

    val booksByAsin = linkedMapOf<String, LibraryBook>()
    val seriesBySourceId = linkedMapOf<String, AudibleSeriesMetadata>()
    for (index in 0 until bookValues.length()) {
      val value = bookValues.optJSONObject(index)
        ?: throw IllegalArgumentException("Audible Web Library の books[$index] が不正です")
      val asin = value.requiredAsin(index)
      val title = value.optString("title").trim()
      require(title.isNotEmpty()) { "Audible Web Library の books[$index] にタイトルがありません" }

      val importedSeries = value.series(index)
      if (importedSeries != null) {
        seriesBySourceId[asin] = importedSeries
      } else {
        seriesBySourceId.remove(asin)
      }

      booksByAsin[asin] = LibraryBook(
        source = LibrarySource.AUDIBLE,
        sourceId = asin,
        title = title,
        authors = value.optJSONArray("authors").stringList(),
        publisher = value.nullableString("publisher"),
        publishedDate = value.nullableString("publishedDate"),
        description = value.nullableString("description")?.plainText(),
        isbn10 = null,
        isbn13 = null,
        thumbnailUrl = value.nullableHttpsUrl("coverUrl"),
        infoUrl = "https://www.audible.co.jp/pd/$asin",
        series = importedSeries?.toLibrarySeries(),
        narrators = value.optJSONArray("narrators").stringList(),
        duration = value.durationMinutes(index)?.toDurationLabel(),
      )
    }

    require(booksByAsin.isNotEmpty()) { "Audible Web Library の蔵書が見つかりませんでした" }
    return AudibleWebLibraryExport(
      books = booksByAsin.values.toList(),
      seriesBySourceId = seriesBySourceId,
    )
  }

  private fun JSONObject.requiredAsin(index: Int): String {
    val asin = optString("asin").trim().uppercase(Locale.ROOT)
    require(AUDIBLE_ASIN.matches(asin)) { "Audible Web Library の books[$index] の ASIN が不正です" }
    return asin
  }

  private fun JSONObject.series(index: Int): AudibleSeriesMetadata? {
    if (!has("series") || isNull("series")) return null
    val series = optJSONObject("series")
      ?: throw IllegalArgumentException("Audible Web Library の books[$index].series が不正です")
    val id = series.nullableString("id")?.uppercase(Locale.ROOT)
    if (id != null) {
      require(AUDIBLE_ASIN.matches(id)) {
        "Audible Web Library の books[$index].series.id が不正です"
      }
    }
    val name = series.nullableString("name")
    require(id != null || name != null) {
      "Audible Web Library の books[$index].series に識別情報がありません"
    }
    val position = if (series.has("position") && !series.isNull("position")) {
      series.opt("position").toString().toIntOrNull()?.also { value ->
        require(value > 0) { "Audible Web Library の books[$index].series.position が不正です" }
      } ?: throw IllegalArgumentException(
        "Audible Web Library の books[$index].series.position が不正です",
      )
    } else {
      null
    }
    return AudibleSeriesMetadata(
      seriesId = id,
      seriesName = name,
      position = position,
    )
  }

  private fun JSONObject.durationMinutes(index: Int): Int? {
    if (!has("durationMinutes") || isNull("durationMinutes")) return null
    val minutes = opt("durationMinutes").toString().toIntOrNull()
      ?: throw IllegalArgumentException("Audible Web Library の books[$index].durationMinutes が不正です")
    require(minutes > 0) { "Audible Web Library の books[$index].durationMinutes が不正です" }
    return minutes
  }

  private fun JSONObject.nullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf(String::isNotEmpty)
  }

  private fun JSONObject.nullableHttpsUrl(key: String): String? {
    val value = nullableString(key) ?: return null
    return runCatching { URI(value) }
      .getOrNull()
      ?.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
      ?.toString()
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

  private fun String.plainText(): String = replace(BREAK_TAG, " ")
    .replace(HTML_TAG, " ")
    .replace(WHITESPACE, " ")
    .trim()

  private fun Int.toDurationLabel(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
      hours > 0 && minutes > 0 -> "${hours}時間${minutes}分"
      hours > 0 -> "${hours}時間"
      else -> "${minutes}分"
    }
  }

  private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { "Audible Web Library の JSON が大きすぎます（上限 25 MB）" }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private val AUDIBLE_ASIN = Regex("^[A-Z0-9]{10}$")
  private val BREAK_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
  private val HTML_TAG = Regex("<[^>]+>")
  private val WHITESPACE = Regex("\\s+")
  private const val EXPORT_FORMAT = "audible-library-export"
  private const val EXPORT_VERSION = 1
  private const val MAX_INPUT_BYTES = 25 * 1024 * 1024
}

internal fun String.isAudibleWebLibraryJson(): Boolean =
  substringAfterLast('/').substringAfterLast('\\').endsWith(".json", ignoreCase = true)

internal fun AudibleSeriesMetadata.toLibrarySeries(): LibrarySeries = LibrarySeries(
  name = seriesName ?: "シリーズ名未取得 (${requireNotNull(seriesId)})",
  position = position,
  id = seriesId,
)
