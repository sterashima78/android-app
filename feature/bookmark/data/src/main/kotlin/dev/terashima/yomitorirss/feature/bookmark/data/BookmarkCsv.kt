package dev.terashima.yomitorirss.feature.bookmark.data
import java.io.Reader
import java.net.URI
import java.time.Instant

internal data class BookmarkCsvEntry(
  val title: String,
  val url: String,
  val createdAt: String,
  val sourceTitle: String,
  val tagNames: List<String>,
)

internal data class BookmarkCsvParseResult(
  val entries: List<BookmarkCsvEntry>,
  val skippedRows: Int,
)

internal fun parseBookmarkCsv(
  reader: Reader,
  fallbackCreatedAt: String = Instant.now().toString(),
): BookmarkCsvParseResult {
  val rows = parseCsvRows(reader.readText())
  require(rows.isNotEmpty()) { "CSVが空です" }

  val header = rows.first().mapIndexed { index, value ->
    value.removePrefix("\uFEFF").trim().lowercase() to index
  }.toMap()
  val urlIndex = header["url"] ?: error("CSVにurl列がありません")
  val titleIndex = header["title"]
  val createdIndex = header["created"] ?: header["created_at"] ?: header["saved_at"]
  val folderIndex = header["folder"]
  val tagsIndex = header["tags"]

  var skippedRows = 0
  val entries = buildList {
    rows.drop(1).forEach { row ->
      if (row.all(String::isBlank)) return@forEach
      val rawUrl = row.cell(urlIndex).trim()
      val uri = runCatching { URI(rawUrl) }.getOrNull()
      val host = uri?.host?.takeIf(String::isNotBlank)
      if (uri?.scheme?.lowercase() !in setOf("http", "https") || host == null) {
        skippedRows += 1
        return@forEach
      }

      val sourceTitle = host.removePrefix("www.")
      val title = titleIndex?.let(row::cell)?.trim().orEmpty().ifBlank { sourceTitle }
      val createdAt = createdIndex
        ?.let(row::cell)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { raw -> runCatching { Instant.parse(raw).toString() }.getOrNull() }
        ?: fallbackCreatedAt
      val folder = folderIndex?.let(row::cell)?.trim().orEmpty()
      val tags = tagsIndex
        ?.let(row::cell)
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)

      val tagNames = buildList {
        if (folder.isNotBlank() && !folder.equals("Unsorted", ignoreCase = true)) add(folder)
        addAll(tags)
      }.distinctBy { normalizeImportedTag(it) }

      add(
        BookmarkCsvEntry(
          title = title,
          url = rawUrl,
          createdAt = createdAt,
          sourceTitle = sourceTitle,
          tagNames = tagNames,
        ),
      )
    }
  }

  return BookmarkCsvParseResult(entries = entries, skippedRows = skippedRows)
}

private fun List<String>.cell(index: Int): String = getOrElse(index) { "" }

private fun normalizeImportedTag(name: String): String =
  name.trim().replace(Regex("\\s+"), " ").lowercase()

private fun parseCsvRows(text: String): List<List<String>> {
  val rows = mutableListOf<List<String>>()
  val row = mutableListOf<String>()
  val field = StringBuilder()
  var inQuotes = false
  var index = 0

  fun finishField() {
    row += field.toString()
    field.setLength(0)
  }

  fun finishRow() {
    finishField()
    rows += row.toList()
    row.clear()
  }

  while (index < text.length) {
    val character = text[index]
    when {
      character == '"' && inQuotes && index + 1 < text.length && text[index + 1] == '"' -> {
        field.append('"')
        index += 2
      }
      character == '"' -> {
        inQuotes = !inQuotes
        index += 1
      }
      character == ',' && !inQuotes -> {
        finishField()
        index += 1
      }
      (character == '\n' || character == '\r') && !inQuotes -> {
        finishRow()
        if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index += 2
        else index += 1
      }
      else -> {
        field.append(character)
        index += 1
      }
    }
  }

  require(!inQuotes) { "CSVの引用符が閉じられていません" }
  if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
  return rows
}
