package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

internal class AudibleLibraryMetadataEnricher {
  fun enrich(
    fileName: String?,
    bytes: ByteArray,
    books: List<LibraryBook>,
  ): List<LibraryBook> {
    if (books.isEmpty()) return books

    val metadata = audibleLibraryContents(fileName, bytes)
      .flatMap(::parseMetadata)
      .associateBy(AudibleMetadata::sourceId)

    return books.map { book ->
      if (book.source != LibrarySource.AUDIBLE) return@map book
      val extra = metadata[book.sourceId]
      book.copy(
        narrators = extra?.narrators.orEmpty(),
        duration = extra?.duration,
        infoUrl = book.infoUrl
          ?.takeIf(String::isNotBlank)
          ?: extra?.infoUrl
          ?: audibleProductUrl(book.sourceId),
      )
    }
  }

  private fun audibleLibraryContents(fileName: String?, bytes: ByteArray): List<String> {
    if (!isZip(fileName, bytes)) {
      if (!fileName.orEmpty().isAudibleLibraryFile()) return emptyList()
      return listOf(bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF"))
    }

    val contents = mutableListOf<String>()
    var entryCount = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entryCount += 1
        require(entryCount <= MAX_ZIP_ENTRIES) { "ZIP 内のファイル数が多すぎます" }
        if (!entry.isDirectory && entry.name.isAudibleLibraryFile()) {
          contents += zip.readLimited(MAX_LIBRARY_BYTES)
            .toString(StandardCharsets.UTF_8)
            .removePrefix("\uFEFF")
        }
        zip.closeEntry()
      }
    }
    return contents
  }

  private fun parseMetadata(text: String): List<AudibleMetadata> {
    val rows = parseRows(text).filterNot { row -> row.all(String::isBlank) }
    if (rows.size < 2) return emptyList()

    val header = rows.first().map(::normalizeHeader)
    val idIndex = header.indexOfAlias(ID_HEADERS) ?: return emptyList()
    val narratorIndexes = header.indexesOfAliases(NARRATOR_HEADERS)
    val durationIndex = header.indexOfAlias(DURATION_HEADERS)
    val infoUrlIndex = header.indexOfAlias(INFO_URL_HEADERS)
    val deletedIndex = header.indexOfAlias(DELETED_HEADERS)

    return rows.drop(1).mapNotNull { row ->
      if (row.valueAt(deletedIndex).isTruthy()) return@mapNotNull null
      val sourceId = row.valueAt(idIndex).clean() ?: return@mapNotNull null
      AudibleMetadata(
        sourceId = sourceId,
        narrators = narratorIndexes
          .flatMap { index -> splitPeople(row.valueAt(index)) }
          .distinctBy { it.lowercase(Locale.ROOT) },
        duration = row.valueAt(durationIndex).clean(),
        infoUrl = row.valueAt(infoUrlIndex).clean(),
      )
    }
  }

  private fun audibleProductUrl(sourceId: String): String? {
    val asin = sourceId.trim().uppercase(Locale.ROOT)
    return asin
      .takeIf { AUDIBLE_ASIN.matches(it) }
      ?.let { "https://www.audible.co.jp/pd/$it" }
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

  private fun ZipInputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { "Audible Library.csv が大きすぎます" }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private fun List<String>.indexOfAlias(aliases: Set<String>): Int? =
    indexOfFirst { it in aliases }.takeIf { it >= 0 }

  private fun List<String>.indexesOfAliases(aliases: Set<String>): List<Int> =
    mapIndexedNotNull { index, value -> index.takeIf { value in aliases } }

  private fun List<String>.valueAt(index: Int?): String? = index?.let { getOrNull(it) }

  private fun splitPeople(value: String?): List<String> = value.clean()
    ?.split(';', '|', '/')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

  private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

  private fun String?.isTruthy(): Boolean = when (clean()?.lowercase(Locale.ROOT)) {
    "true", "1", "yes", "y" -> true
    else -> false
  }

  private fun normalizeHeader(value: String): String = value
    .removePrefix("\uFEFF")
    .trim()
    .lowercase(Locale.ROOT)
    .filter(Char::isLetterOrDigit)

  private fun String.baseName(): String =
    substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)

  private fun String.isAudibleLibraryFile(): Boolean = baseName() == "library.csv"

  private fun isZip(fileName: String?, bytes: ByteArray): Boolean =
    fileName?.lowercase(Locale.ROOT)?.endsWith(".zip") == true ||
      (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())

  private data class AudibleMetadata(
    val sourceId: String,
    val narrators: List<String>,
    val duration: String?,
    val infoUrl: String?,
  )

  private companion object {
    const val MAX_ZIP_ENTRIES = 100
    const val MAX_LIBRARY_BYTES = 25 * 1024 * 1024

    val AUDIBLE_ASIN = Regex("^[A-Z0-9]{10}$")
    val ID_HEADERS = setOf("asin", "amazonasin", "audibleasin", "productid", "contentid", "id")
    val NARRATOR_HEADERS = setOf("narrator", "narrators", "narratedby")
    val DURATION_HEADERS = setOf(
      "duration",
      "length",
      "runningtime",
      "runtime",
      "runtimelength",
      "listeninglength",
    )
    val INFO_URL_HEADERS = setOf("infourl", "producturl", "detailurl", "bookurl", "url")
    val DELETED_HEADERS = setOf("deleted", "isdeleted", "deletedfromlibrary", "isdeletedfromlibrary")
  }
}
