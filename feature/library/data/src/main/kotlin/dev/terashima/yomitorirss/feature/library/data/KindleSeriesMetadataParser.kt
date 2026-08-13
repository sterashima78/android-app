package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibrarySeries
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object KindleSeriesMetadataParser {
  fun matches(path: String): Boolean =
    path.substringAfterLast('/').substringAfterLast('\\').equals(
      SERIES_METADATA_FILE_NAME,
      ignoreCase = true,
    )

  fun parse(bytes: ByteArray): Map<String, LibrarySeries> {
    val text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    val rows = parseRows(text, ',').filterNot { row -> row.all(String::isBlank) }
    if (rows.size < 2) return emptyMap()

    val header = rows.first().map(::normalizeHeader)
    val recordTypeIndex = header.indexOf("recordtype")
    val seriesIdIndex = header.indexOf("seriesasin")
    val seriesNameIndex = header.indexOf("seriesproductname")
    val itemIdIndex = header.indexOf("itemasin")
    val itemPositionIndex = header.indexOf("itempositioninseries")
    if (
      recordTypeIndex < 0 ||
      seriesNameIndex < 0 ||
      itemIdIndex < 0 ||
      itemPositionIndex < 0
    ) {
      return emptyMap()
    }

    return buildMap {
      rows.drop(1).forEach { row ->
        if (!row.valueAt(recordTypeIndex).equals("Item", ignoreCase = true)) return@forEach

        val itemId = row.valueAt(itemIdIndex).amazonValue() ?: return@forEach
        val seriesName = row.valueAt(seriesNameIndex).amazonValue() ?: return@forEach
        val zeroBasedPosition = row.valueAt(itemPositionIndex)
          .amazonValue()
          ?.toIntOrNull()
          ?.takeIf { it >= 0 && it < Int.MAX_VALUE }
          ?: return@forEach

        put(
          itemId,
          LibrarySeries(
            name = seriesName,
            position = zeroBasedPosition + 1,
            sourceId = row.valueAt(seriesIdIndex).amazonValue(),
          ),
        )
      }
    }
  }

  private fun parseRows(text: String, delimiter: Char): List<List<String>> {
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
        char == delimiter && !quoted -> finishField()
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
