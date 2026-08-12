package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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

    val imported = selectContentsForSource(source, contents)
      .flatMap { content -> parseContent(source, content) }
      .distinctBy(LibraryBook::sourceId)

    require(imported.isNotEmpty()) { source.unrecognizedImportMessage() }
    return imported
  }

  private fun selectContentsForSource(
    source: LibrarySource,
    contents: List<ImportContent>,
  ): List<ImportContent> = when (source) {
    LibrarySource.KINDLE -> selectKindleContents(contents)
    LibrarySource.AUDIBLE -> selectAudibleContents(contents)
    LibrarySource.GOOGLE_PLAY_BOOKS -> emptyList()
  }

  private fun selectKindleContents(contents: List<ImportContent>): List<ImportContent> {
    val ownership = contents.filter { it.name.isKindleOwnershipFile() }
    if (ownership.isNotEmpty()) {
      val kindleScoped = ownership.filter { it.name.hasKindlePathHint() }
      return kindleScoped.ifEmpty { ownership }
    }

    // 旧インポートとの互換性のため、単一ファイルを明示選択した場合だけ汎用解析を残す。
    // 複数ファイル ZIP では行動ログ等を蔵書と誤認しない。
    if (contents.size == 1) {
      val only = contents.single()
      return if (only.name.isJsonFile() || only.name.isDelimitedTextFile()) listOf(only) else emptyList()
    }
    return emptyList()
  }

  private fun selectAudibleContents(contents: List<ImportContent>): List<ImportContent> {
    val library = contents.filter { it.name.isAudibleLibraryFile() }
    if (library.isNotEmpty()) return library

    // Audible のフルエクスポートにはタイトルを含む履歴系 CSV があるため、
    // 単一ファイル選択時も既知の非蔵書ファイルは明示的に拒否する。
    if (contents.size == 1) {
      val only = contents.single()
      if (only.name.isKnownAudibleNonLibraryFile()) return emptyList()
      return if (only.name.isDelimitedTextFile()) listOf(only) else emptyList()
    }
    return emptyList()
  }

  private fun parseContent(
    source: LibrarySource,
    content: ImportContent,
  ): List<LibraryBook> {
    val text = content.bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    if (source == LibrarySource.KINDLE && content.name.isJsonFile()) {
      return parseKindleOwnershipJson(text)
    }

    val delimiters = when {
      content.name.lowercase(Locale.ROOT).endsWith(".tsv") -> listOf('\t', ',')
      else -> listOf(',', '\t')
    }
    return delimiters.firstNotNullOfOrNull { delimiter ->
      parseDelimited(source, text, delimiter).takeIf(List<LibraryBook>::isNotEmpty)
    }.orEmpty()
  }

  private fun parseKindleOwnershipJson(text: String): List<LibraryBook> {
    val roots = parseJsonRoots(text)
    if (roots.isEmpty()) return emptyList()

    val candidates = mutableListOf<KindleOwnershipCandidate>()
    var ordinal = 0
    roots.forEach { root ->
      collectKindleOwnershipCandidates(root, candidates) { ordinal++ }
    }

    return candidates
      .groupBy(KindleOwnershipCandidate::sourceId)
      .mapNotNull { (_, records) ->
        val latest = records.maxWithOrNull(
          compareBy<KindleOwnershipCandidate> { it.eventEpochMillis ?: Long.MIN_VALUE }
            .thenBy(KindleOwnershipCandidate::ordinal),
        ) ?: return@mapNotNull null
        if (latest.state == KindleRightState.REVOKED) return@mapNotNull null

        records
          .asSequence()
          .filter { it.book != null }
          .maxWithOrNull(
            compareBy<KindleOwnershipCandidate> { it.eventEpochMillis ?: Long.MIN_VALUE }
              .thenBy(KindleOwnershipCandidate::ordinal),
          )
          ?.book
      }
  }

  private fun parseJsonRoots(text: String): List<Any> = runCatching {
    buildList {
      val tokener = JSONTokener(text)
      while (tokener.more()) {
        val next = tokener.nextClean()
        if (next.code == 0) break
        tokener.back()
        add(tokener.nextValue())
      }
    }
  }.getOrElse { emptyList() }

  private fun collectKindleOwnershipCandidates(
    value: Any?,
    output: MutableList<KindleOwnershipCandidate>,
    nextOrdinal: () -> Int,
  ) {
    when (value) {
      is JSONObject -> {
        val candidate = kindleOwnershipCandidate(value, nextOrdinal())
        if (candidate != null) {
          output += candidate
          return
        }
        val keys = value.keys()
        while (keys.hasNext()) {
          collectKindleOwnershipCandidates(value.opt(keys.next()), output, nextOrdinal)
        }
      }
      is JSONArray -> {
        for (index in 0 until value.length()) {
          collectKindleOwnershipCandidates(value.opt(index), output, nextOrdinal)
        }
      }
    }
  }

  private fun kindleOwnershipCandidate(
    objectValue: JSONObject,
    ordinal: Int,
  ): KindleOwnershipCandidate? {
    val values = linkedMapOf<String, MutableList<String>>()
    collectPrimitiveValues(objectValue, values)

    val sourceId = values.firstValue(KINDLE_ID_HEADERS)?.trim()?.takeIf(String::isNotEmpty)
      ?: return null
    if (values.isKnownNonBookContent()) return null

    val state = values.rightState()
    val title = values.firstValue(KINDLE_TITLE_HEADERS)?.trim()?.takeIf(String::isNotEmpty)
    if (title == null && state == null) return null

    val authors = KINDLE_AUTHOR_HEADERS
      .flatMap { header -> values[header].orEmpty() }
      .flatMap(::splitPeople)
      .distinctBy { it.lowercase(Locale.ROOT) }
    val eventTimestamp = values.firstValue(KINDLE_EVENT_DATE_HEADERS)
    val book = title?.let {
      LibraryBook(
        source = LibrarySource.KINDLE,
        sourceId = sourceId,
        title = it,
        authors = authors,
        publisher = values.firstValue(PUBLISHER_HEADERS),
        publishedDate = values.firstValue(KINDLE_PUBLISHED_DATE_HEADERS),
        description = values.firstValue(DESCRIPTION_HEADERS),
        isbn10 = values.firstValue(ISBN10_HEADERS).cleanIsbn(),
        isbn13 = values.firstValue(ISBN13_HEADERS).cleanIsbn(),
        thumbnailUrl = values.firstValue(THUMBNAIL_HEADERS),
        infoUrl = values.firstValue(INFO_URL_HEADERS),
      )
    }

    return KindleOwnershipCandidate(
      sourceId = sourceId,
      book = book,
      state = state,
      eventEpochMillis = eventTimestamp.toEpochMillisOrNull(),
      ordinal = ordinal,
    )
  }

  private fun collectPrimitiveValues(
    objectValue: JSONObject,
    output: MutableMap<String, MutableList<String>>,
  ) {
    val keys = objectValue.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      when (val value = objectValue.opt(key)) {
        null, JSONObject.NULL -> Unit
        is JSONObject -> collectPrimitiveValues(value, output)
        is JSONArray -> {
          for (index in 0 until value.length()) {
            val item = value.opt(index)
            if (item != null && item != JSONObject.NULL && item !is JSONObject && item !is JSONArray) {
              output.getOrPut(normalizeHeader(key)) { mutableListOf() } += item.toString()
            }
          }
        }
        else -> output.getOrPut(normalizeHeader(key)) { mutableListOf() } += value.toString()
      }
    }
  }

  private fun Map<String, List<String>>.firstValue(headers: List<String>): String? =
    headers.firstNotNullOfOrNull { header -> this[header]?.firstOrNull(String::isNotBlank) }

  private fun Map<String, List<String>>.isKnownNonBookContent(): Boolean {
    val type = firstValue(KINDLE_CONTENT_TYPE_HEADERS)?.lowercase(Locale.ROOT) ?: return false
    return KINDLE_NON_BOOK_TYPE_MARKERS.any(type::contains)
  }

  private fun Map<String, List<String>>.rightState(): KindleRightState? {
    val action = firstValue(KINDLE_RIGHT_ACTION_HEADERS)?.lowercase(Locale.ROOT) ?: return null
    return when {
      KINDLE_REVOKED_MARKERS.any(action::contains) -> KindleRightState.REVOKED
      KINDLE_GRANTED_MARKERS.any(action::contains) -> KindleRightState.GRANTED
      else -> null
    }
  }

  private fun String?.toEpochMillisOrNull(): Long? {
    val value = clean() ?: return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
      ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
      ?: runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
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
    val publisherIndex = header.indexOfAlias(PUBLISHER_HEADERS.toSet())
    val publishedDateIndex = header.indexOfAlias(PUBLISHED_DATE_HEADERS.toSet())
    val descriptionIndex = header.indexOfAlias(DESCRIPTION_HEADERS.toSet())
    val isbn10Index = header.indexOfAlias(ISBN10_HEADERS.toSet())
    val isbn13Index = header.indexOfAlias(ISBN13_HEADERS.toSet())
    val isbnIndex = header.indexOfAlias(ISBN_HEADERS)
    val thumbnailIndex = header.indexOfAlias(THUMBNAIL_HEADERS.toSet())
    val infoUrlIndex = header.indexOfAlias(INFO_URL_HEADERS.toSet())
    val deletedIndex = if (source == LibrarySource.AUDIBLE) {
      header.indexOfAlias(AUDIBLE_DELETED_HEADERS)
    } else {
      null
    }

    return rows.drop(1).mapNotNull { row ->
      if (row.valueAt(deletedIndex).isTruthy()) return@mapNotNull null

      val title = row.valueAt(titleIndex)?.trim().orEmpty()
      if (title.isBlank()) return@mapNotNull null

      val authors = authorIndexes
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
        if (!entry.isDirectory && entry.name.isSupportedImportFile()) {
          val remaining = MAX_EXPANDED_BYTES - expandedBytes
          require(remaining > 0) { "ZIP の展開サイズが大きすぎます（上限 50 MB）" }
          val content = zip.readLimited(minOf(MAX_ENTRY_BYTES, remaining.toInt()))
          expandedBytes += content.size
          contents += ImportContent(entry.name, content)
        }
        zip.closeEntry()
      }
    }
    require(contents.isNotEmpty()) { "ZIP に対応する蔵書データファイルが見つかりません" }
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

  private fun String.isKindleOwnershipFile(): Boolean {
    val name = baseName()
    return name.startsWith("digital.content.ownership") && name.endsWith(".json")
  }

  private fun String.hasKindlePathHint(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return "kindle" in lower || "ebook" in lower || "e-book" in lower
  }

  private fun String.isAudibleLibraryFile(): Boolean = when (baseName()) {
    "library.csv", "library.tsv" -> true
    else -> false
  }

  private fun String.isKnownAudibleNonLibraryFile(): Boolean =
    baseName() in AUDIBLE_NON_LIBRARY_FILE_NAMES

  private fun String.isDelimitedTextFile(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")
  }

  private fun String.isJsonFile(): Boolean = lowercase(Locale.ROOT).endsWith(".json")

  private fun String.isSupportedImportFile(): Boolean = isDelimitedTextFile() || isJsonFile()

  private fun isZip(fileName: String?, bytes: ByteArray): Boolean =
    fileName?.lowercase(Locale.ROOT)?.endsWith(".zip") == true ||
      (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte())

  private fun LibrarySource.unrecognizedImportMessage(): String = when (this) {
    LibrarySource.KINDLE ->
      "Kindle 蔵書を認識できませんでした。Digital.Content.Ownership*.json、対応 CSV/TSV、またはそれらを含む ZIP を選択してください"
    LibrarySource.AUDIBLE ->
      "Audible 蔵書を認識できませんでした。Library.csv、対応 CSV/TSV、または Library.csv を含む ZIP を選択してください"
    LibrarySource.GOOGLE_PLAY_BOOKS -> "対応していない蔵書ソースです"
  }

  private data class ImportContent(val name: String, val bytes: ByteArray)

  private data class KindleOwnershipCandidate(
    val sourceId: String,
    val book: LibraryBook?,
    val state: KindleRightState?,
    val eventEpochMillis: Long?,
    val ordinal: Int,
  )

  private enum class KindleRightState {
    GRANTED,
    REVOKED,
  }

  private companion object {
    const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 50 * 1024 * 1024L
    const val MAX_ENTRY_BYTES = 25 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 100

    val TITLE_HEADERS = setOf("title", "booktitle", "producttitle", "itemname", "name")
    val ID_HEADERS = setOf("asin", "amazonasin", "audibleasin", "productid", "contentid", "id")
    val AUTHOR_HEADERS = setOf("author", "authors", "creator", "creators", "writtenby")
    val PUBLISHER_HEADERS = listOf("publisher", "publishername")
    val PUBLISHED_DATE_HEADERS = listOf(
      "publisheddate",
      "publicationdate",
      "releasedate",
      "releasedatetime",
    )
    val DESCRIPTION_HEADERS = listOf("description", "summary", "productdescription")
    val ISBN10_HEADERS = listOf("isbn10")
    val ISBN13_HEADERS = listOf("isbn13")
    val ISBN_HEADERS = setOf("isbn")
    val THUMBNAIL_HEADERS = listOf("thumbnailurl", "imageurl", "coverurl", "coverimageurl")
    val INFO_URL_HEADERS = listOf("infourl", "producturl", "detailurl", "url")
    val AUDIBLE_DELETED_HEADERS = setOf("deleted", "isdeleted", "deletedfromlibrary", "isdeletedfromlibrary")

    val KINDLE_ID_HEADERS = listOf("asin", "amazonasin", "productasin", "contentasin", "contentid")
    val KINDLE_TITLE_HEADERS = listOf("title", "booktitle", "producttitle", "contenttitle", "itemtitle", "name")
    val KINDLE_AUTHOR_HEADERS = listOf("author", "authors", "creator", "creators", "writtenby")
    val KINDLE_PUBLISHED_DATE_HEADERS = listOf(
      "publisheddate",
      "publicationdate",
      "releasedate",
      "releasedatetime",
    )
    val KINDLE_EVENT_DATE_HEADERS = listOf(
      "eventtimestamp",
      "timestamp",
      "updatedat",
      "createdat",
      "acquisitiondate",
      "purchasedate",
      "date",
    )
    val KINDLE_RIGHT_ACTION_HEADERS = listOf(
      "righttype",
      "rightaction",
      "action",
      "eventtype",
      "operation",
      "status",
      "right",
    )
    val KINDLE_CONTENT_TYPE_HEADERS = listOf(
      "contenttype",
      "digitalcontenttype",
      "producttype",
      "mediatype",
      "assettype",
      "format",
    )
    val KINDLE_REVOKED_MARKERS = listOf("revoke", "return", "expire", "delete", "remove")
    val KINDLE_GRANTED_MARKERS = listOf("grant", "purchase", "acquire", "own")
    val KINDLE_NON_BOOK_TYPE_MARKERS = listOf("music", "song", "album", "video", "movie", "audible", "audiobook")

    val AUDIBLE_NON_LIBRARY_FILE_NAMES = setOf(
      "account details.csv",
      "cart history.csv",
      "collections.csv",
      "customer segment.csv",
      "customer settings global.csv",
      "device activations.csv",
      "impressions data.csv",
      "listening history.csv",
      "membership billing.csv",
      "membership history.csv",
      "onboarding preferences.csv",
      "purchase history.csv",
      "wishlist.csv",
    )
  }
}
