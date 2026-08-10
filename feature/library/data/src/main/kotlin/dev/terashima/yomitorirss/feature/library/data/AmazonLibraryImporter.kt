package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

internal class AmazonLibraryImporter {
  fun parse(
    source: LibrarySource,
    fileName: String?,
    bytes: ByteArray,
  ): List<LibraryBook> {
    require(source == LibrarySource.KINDLE || source == LibrarySource.AUDIBLE) {
      "Kindle または Audible のファイルを選択してください"
    }
    require(bytes.isNotEmpty()) { "インポートファイルが空です" }
    require(bytes.size <= MAX_INPUT_BYTES) { "インポートファイルが大きすぎます（上限 25 MB）" }

    val contents = if (isZip(fileName, bytes)) {
      readZipContents(bytes)
    } else {
      listOf(ImportContent(fileName.orEmpty(), bytes))
    }

    val imported = contents
      .flatMap { content -> parseContent(source, content) }
      .distinctBy(LibraryBook::sourceId)

    require(imported.isNotEmpty()) {
      "蔵書データを認識できませんでした。CSV / TSV またはそれらを含む ZIP を選択してください"
    }
    return imported
  }

  private fun parseContent(
    source: LibrarySource,
    content: ImportContent,
  ): List<LibraryBook> {
    val text = content.bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    val delimiters = when {
      content.name.lowercase(Locale.ROOT).endsWith(".tsv") -> listOf('\t', ',')
      else -> listOf(',', '\t')
    }
    return delimiters.firstNotNullOfOrNull { delimiter ->
      parseDelimited(source, text, delimiter).takeIf(List<LibraryBook>::isNotEmpty)
    }.orEmpty()
  }

  private fun parseDelimited(
    source: LibrarySource,
    text: String,
    delimiter: Char,
  ): List<LibraryBook> {
    val rows = parseRows(text, delimiter).filterNot { row -> row.all(String::isBlank) }
    if (rows.size < 2) return emptyList()

    val header = rows.first().map(::normalizeHeader)
    val titleIndex = header.indexOfAlias(TITLE_HEADERS) ?: return emptyList()
    val idIndex = header.indexOfAlias(ID_HEADERS)
    val authorIndexes = header.indexesOfAliases(AUTHOR_HEADERS)
    val narratorIndexes = if (source == LibrarySource.AUDIBLE) {
      header.indexesOfAliases(NARRATOR_HEADERS)
    } else {
      emptyList()
    }
    val publisherIndex = header.indexOfAlias(PUBLISHER_HEADERS)
    val publishedDateIndex = header.indexOfAlias(PUBLISHED_DATE_HEADERS)
    val descriptionIndex = header.indexOfAlias(DESCRIPTION_HEADERS)
    val isbn10Index = header.indexOfAlias(ISBN10_HEADERS)
    val isbn13Index = header.indexOfAlias(ISBN13_HEADERS)
    val isbnIndex = header.indexOfAlias(ISBN_HEADERS)
    val thumbnailIndex = header.indexOfAlias(THUMBNAIL_HEADERS)
    val infoUrlIndex = header.indexOfAlias(INFO_URL_HEADERS)

    return rows.drop(1).mapNotNull { row ->
      val title = row.valueAt(titleIndex)?.trim().orEmpty()
      if (title.isBlank()) return@mapNotNull null

      val authors = (authorIndexes + narratorIndexes)
        .flatMap { index -> splitPeople(row.valueAt(index)) }
        .distinctBy { it.lowercase(Locale.ROOT) }
      val publishedDate = row.valueAt(publishedDateIndex).clean()
      val explicitId = row.valueAt(idIndex).clean()
      val genericIsbn = row.valueAt(isbnIndex).cleanIsbn()
      val isbn10 = row.valueAt(isbn10Index).cleanIsbn()
        ?: genericIsbn?.takeIf { it.length == 10 }
      val isbn13 = row.valueAt(isbn13Index).cleanIsbn()
        ?: genericIsbn?.takeIf { it.length == 13 }
      val sourceId = explicitId ?: derivedSourceId(source, title, authors, publishedDate)

      LibraryBook(
        source = source,
        sourceId = sourceId,
        title = title,
        authors = authors,
        publisher = row.valueAt(publisherIndex).clean(),
        publishedDate = publishedDate,
        description = row.valueAt(descriptionIndex).clean(),
        isbn10 = isbn10,
        isbn13 = isbn13,
        thumbnailUrl = row.valueAt(thumbnailIndex).clean(),
        infoUrl = row.valueAt(infoUrlIndex).clean(),
      )
    }
  }

  private fun readZipContents(bytes: ByteArray): List<ImportContent> {
    val contents = mutableListOf<ImportContent>()
    var entryCount = 0
    var expandedBytes = 0L

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entryCount += 1
        require(entryCount <= MAX_ZIP_ENTRIES) { "ZIP 内のファイル数が多すぎます" }
        if (!entry.isDirectory && entry.name.isDelimitedTextFile()) {
          val remaining = MAX_EXPANDED_BYTES - expandedBytes
          require(remaining > 0) { "ZIP の展開サイズが大きすぎます（上限 50 MB）" }
          val content = zip.readLimited(minOf(MAX_ENTRY_BYTES, remaining.toInt()))
          expandedBytes += content.size
          contents += ImportContent(entry.name, content)
        }
        zip.closeEntry()
      }
    }
    require(contents.isNotEmpty()) { "ZIP に CSV / TSV ファイルが見つかりません" }
    return contents
  }

  private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
      val read = read(buffer)
      if (read < 0) break
      total += read
      require(total <= limit) { "ZIP 内のファイルが大きすぎます" }
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
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

  private fun derivedSourceId(
    source: LibrarySource,
    title: String,
    authors: List<String>,
    publishedDate: String?,
  ): String {
    val seed = buildString {
      append(source.name)
      append('\u0000')
      append(title.trim().lowercase(Locale.ROOT))
      append('\u0000')
      append(authors.joinToString("|") { it.trim().lowercase(Locale.ROOT) })
      append('\u0000')
      append(publishedDate.orEmpty().trim())
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(StandardCharsets.UTF_8))
    return "derived:" + digest.joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun List<String>.indexOfAlias(aliases: Set<String>): Int? =
    indexOfFirst { it in aliases }.takeIf { it >= 0 }

  private fun List<String>.indexesOfAliases(aliases: Set<String>): List<Int> =
    mapIndexedNotNull { index, value -> index.takeIf { value in aliases } }

  private fun List<String>.valueAt(index: Int?): String? =
    index?.let { getOrNull(it) }

  private fun splitPeople(value: String?): List<String> = value.clean()
    ?.split(';', '|', '/')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

  private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

  private fun String?.cleanIsbn(): String? = clean()
    ?.filter { it.isDigit() || it == 'X' || it == 'x' }
    ?.uppercase(Locale.ROOT)
    ?.takeIf { it.length == 10 || it.length == 13 }

  private fun normalizeHeader(value: String): String = value
    .removePrefix("\uFEFF")
    .trim()
    .lowercase(Locale.ROOT)
    .filter(Char::isLetterOrDigit)

  private fun String.isDelimitedTextFile(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")
  }

  private fun isZip(fileName: String?, bytes: ByteArray): Boolean =
    fileName?.lowercase(Locale.ROOT)?.endsWith(".zip") == true ||
      (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())

  private data class ImportContent(val name: String, val bytes: ByteArray)

  private companion object {
    const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 50 * 1024 * 1024L
    const val MAX_ENTRY_BYTES = 25 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 100

    val TITLE_HEADERS = setOf("title", "booktitle", "producttitle", "itemname", "name")
    val ID_HEADERS = setOf("asin", "amazonasin", "audibleasin", "productid", "contentid", "id")
    val AUTHOR_HEADERS = setOf("author", "authors", "creator", "creators", "writtenby")
    val NARRATOR_HEADERS = setOf("narrator", "narrators", "narratedby")
    val PUBLISHER_HEADERS = setOf("publisher", "publishername")
    val PUBLISHED_DATE_HEADERS = setOf(
      "publisheddate",
      "publicationdate",
      "releasedate",
      "releasedatetime",
      "date",
    )
    val DESCRIPTION_HEADERS = setOf("description", "summary", "productdescription")
    val ISBN10_HEADERS = setOf("isbn10")
    val ISBN13_HEADERS = setOf("isbn13")
    val ISBN_HEADERS = setOf("isbn")
    val THUMBNAIL_HEADERS = setOf("thumbnailurl", "imageurl", "coverurl", "coverimageurl")
    val INFO_URL_HEADERS = setOf("infourl", "producturl", "detailurl", "url")
  }
}
