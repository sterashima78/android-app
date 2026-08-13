package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
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

    if (source == LibrarySource.KINDLE) {
      return ByteArrayInputStream(bytes).use { input -> parseKindle(fileName, input) }
    }

    val contents = if (isZip(fileName, bytes)) {
      readZipContents(source, bytes)
    } else {
      val name = fileName.orEmpty()
      require(name.isExpectedSourceFile(source)) { source.unrecognizedImportMessage() }
      listOf(ImportContent(name, bytes))
    }

    val imported = contents
      .flatMap { parseAudibleLibraryCsv(it.bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")) }
      .distinctBy(LibraryBook::sourceId)

    require(imported.isNotEmpty()) { source.unrecognizedImportMessage() }
    return imported
  }

  fun parseKindle(
    fileName: String?,
    input: InputStream,
  ): List<LibraryBook> {
    val buffered = input.asBufferedInputStream()
    val imported = if (isZip(fileName, buffered)) {
      parseKindleZip(buffered)
    } else {
      val name = fileName.orEmpty()
      require(name.isKindleOwnershipFile()) { LibrarySource.KINDLE.unrecognizedImportMessage() }
      val bytes = buffered.readLimited(
        limit = MAX_ENTRY_BYTES,
        tooLargeMessage = "Kindle ownership JSON が大きすぎます（1ファイル上限 25 MB）",
      )
      require(bytes.isNotEmpty()) { "インポートファイルが空です" }
      parseKindleOwnershipContents(listOf(ImportContent(name, bytes)))
    }

    require(imported.isNotEmpty()) { LibrarySource.KINDLE.unrecognizedImportMessage() }
    return imported
  }

  private fun parseKindleZip(input: InputStream): List<LibraryBook> {
    val state = KindleZipScanState()
    scanKindleZip(input = input, depth = 0, state = state)

    require(state.ownershipFileFound) {
      "ZIP 内を全階層走査しましたが Digital.Content.Ownership*.json が見つかりませんでした"
    }
    val imported = resolveKindleOwnershipCandidates(state.candidates)
    require(imported.isNotEmpty()) {
      "Digital.Content.Ownership*.json は見つかりましたが Kindle 蔵書を解析できませんでした"
    }
    return imported
  }

  private fun scanKindleZip(
    input: InputStream,
    depth: Int,
    state: KindleZipScanState,
  ) {
    require(depth <= MAX_NESTED_ZIP_DEPTH) {
      "ZIP の入れ子が深すぎます（上限 $MAX_NESTED_ZIP_DEPTH 階層）"
    }

    ZipInputStream(NonClosingInputStream(input)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        state.entryCount += 1
        require(state.entryCount <= MAX_STREAMING_ZIP_ENTRIES) {
          "ZIP 内のファイル数が多すぎます（上限 100000 件）"
        }

        when {
          entry.isDirectory -> Unit
          entry.name.isKindleOwnershipFile() -> {
            state.ownershipFileFound = true
            val remaining = MAX_KINDLE_EXPANDED_BYTES - state.expandedOwnershipBytes
            require(remaining > 0) {
              "Kindle ownership JSON の合計サイズが大きすぎます（上限 256 MB）"
            }
            val entryLimit = minOf(MAX_ENTRY_BYTES.toLong(), remaining).toInt()
            val tooLargeMessage = if (remaining < MAX_ENTRY_BYTES) {
              "Kindle ownership JSON の合計サイズが大きすぎます（上限 256 MB）"
            } else {
              "Kindle ownership JSON が大きすぎます（1ファイル上限 25 MB）"
            }
            val bytes = zip.readLimited(
              limit = entryLimit,
              tooLargeMessage = tooLargeMessage,
            )
            state.expandedOwnershipBytes += bytes.size
            collectKindleOwnershipContent(bytes, state.candidates) { state.ordinal++ }
          }
          entry.name.isZipFile() -> {
            scanKindleZip(
              input = zip,
              depth = depth + 1,
              state = state,
            )
          }
        }
        zip.closeEntry()
      }
    }
  }

  private fun parseKindleOwnershipContents(contents: List<ImportContent>): List<LibraryBook> {
    val candidates = mutableListOf<KindleOwnershipCandidate>()
    var ordinal = 0
    contents.forEach { content ->
      collectKindleOwnershipContent(content.bytes, candidates) { ordinal++ }
    }
    return resolveKindleOwnershipCandidates(candidates)
  }

  private fun collectKindleOwnershipContent(
    bytes: ByteArray,
    output: MutableList<KindleOwnershipCandidate>,
    nextOrdinal: () -> Int,
  ) {
    val text = bytes.toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
    parseJsonRoots(text).forEach { root ->
      collectKindleOwnershipCandidates(root, output, nextOrdinal)
    }
  }

  private fun resolveKindleOwnershipCandidates(
    candidates: List<KindleOwnershipCandidate>,
  ): List<LibraryBook> = candidates
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
    fun collectArrayValues(key: String, array: JSONArray) {
      for (index in 0 until array.length()) {
        when (val item = array.opt(index)) {
          null, JSONObject.NULL -> Unit
          is JSONObject -> collectPrimitiveValues(item, output)
          is JSONArray -> collectArrayValues(key, item)
          else -> output.getOrPut(normalizeHeader(key)) { mutableListOf() } += item.toString()
        }
      }
    }

    val keys = objectValue.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      when (val value = objectValue.opt(key)) {
        null, JSONObject.NULL -> Unit
        is JSONObject -> collectPrimitiveValues(value, output)
        is JSONArray -> collectArrayValues(key, value)
        else -> output.getOrPut(normalizeHeader(key)) { mutableListOf() } += value.toString()
      }
    }
  }

  private fun Map<String, List<String>>.firstValue(headers: List<String>): String? =
    headers.firstNotNullOfOrNull { header -> this[header]?.firstOrNull(String::isNotBlank) }

  private fun Map<String, List<String>>.isKnownNonBookContent(): Boolean {
    val contentTypes = KINDLE_CONTENT_TYPE_HEADERS
      .flatMap { header -> this[header].orEmpty() }
      .map { it.lowercase(Locale.ROOT) }
    if (contentTypes.any { type -> KINDLE_NON_BOOK_TYPE_MARKERS.any(type::contains) }) return true

    val originTypes = KINDLE_ORIGIN_TYPE_HEADERS
      .flatMap { header -> this[header].orEmpty() }
      .map { it.lowercase(Locale.ROOT) }
    if (originTypes.any { origin -> KINDLE_SYSTEM_CONTENT_ORIGIN_MARKERS.any(origin::contains) }) return true

    val title = firstValue(KINDLE_TITLE_HEADERS)
      ?.trim()
      ?.lowercase(Locale.ROOT)
      ?: return false
    val isKindleBrandedGuide = title.startsWith("kindle") || title.startsWith("amazon kindle")
    return isKindleBrandedGuide && KINDLE_SYSTEM_GUIDE_TITLE_MARKERS.any(title::contains)
  }

  private fun Map<String, List<String>>.rightState(): KindleRightState? {
    val statuses = KINDLE_RIGHT_STATUS_HEADERS
      .flatMap { header -> this[header].orEmpty() }
      .map { it.lowercase(Locale.ROOT) }
    if (statuses.any { it in KINDLE_ACTIVE_STATUS_MARKERS }) {
      return KindleRightState.GRANTED
    }
    if (statuses.any { it in KINDLE_INACTIVE_STATUS_MARKERS }) {
      return KindleRightState.REVOKED
    }

    val actions = KINDLE_RIGHT_ACTION_HEADERS
      .flatMap { header -> this[header].orEmpty() }
      .map { it.lowercase(Locale.ROOT) }
    return when {
      actions.any { action -> KINDLE_REVOKED_MARKERS.any(action::contains) } -> KindleRightState.REVOKED
      actions.any { action -> KINDLE_GRANTED_MARKERS.any(action::contains) } -> KindleRightState.GRANTED
      else -> null
    }
  }

  private fun String?.toEpochMillisOrNull(): Long? {
    val value = clean() ?: return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
      ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
      ?: runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
  }

  private fun parseAudibleLibraryCsv(text: String): List<LibraryBook> {
    val rows = parseRows(text, ',').filterNot { row -> row.all(String::isBlank) }
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
    val deletedIndex = header.indexOfAlias(AUDIBLE_DELETED_HEADERS)

    return rows.drop(1).mapNotNull { row ->
      if (row.valueAt(deletedIndex).isTruthy()) return@mapNotNull null

      val title = row.valueAt(titleIndex)?.trim().orEmpty()
      if (title.isBlank()) return@mapNotNull null

      val authors = authorIndexes
        .flatMap { index -> splitPeople(row.valueAt(index)) }
        .distinctBy { it.lowercase(Locale.ROOT) }
      val publishedDate = row.valueAt(publishedDateIndex).clean()
      val genericIsbn = row.valueAt(isbnIndex).cleanIsbn()
      val isbn10 = row.valueAt(isbn10Index).cleanIsbn()
        ?: genericIsbn?.takeIf { it.length == 10 }
      val isbn13 = row.valueAt(isbn13Index).cleanIsbn()
        ?: genericIsbn?.takeIf { it.length == 13 }
      val sourceId = row.valueAt(idIndex).clean()
        ?: derivedAudibleSourceId(title, authors, publishedDate)

      LibraryBook(
        source = LibrarySource.AUDIBLE,
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

  private fun readZipContents(source: LibrarySource, bytes: ByteArray): List<ImportContent> {
    val contents = mutableListOf<ImportContent>()
    var entryCount = 0
    var expandedBytes = 0L

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entryCount += 1
        require(entryCount <= MAX_ZIP_ENTRIES) { "ZIP 内のファイル数が多すぎます" }
        if (!entry.isDirectory && entry.name.isExpectedSourceFile(source)) {
          val remaining = MAX_EXPANDED_BYTES - expandedBytes
          require(remaining > 0) { "ZIP の展開サイズが大きすぎます（上限 50 MB）" }
          val content = zip.readLimited(
            limit = minOf(MAX_ENTRY_BYTES, remaining.toInt()),
            tooLargeMessage = "ZIP 内のファイルが大きすぎます",
          )
          expandedBytes += content.size
          contents += ImportContent(entry.name, content)
        }
        zip.closeEntry()
      }
    }
    require(contents.isNotEmpty()) { source.unrecognizedImportMessage() }
    return contents
  }

  private fun InputStream.readLimited(
    limit: Int,
    tooLargeMessage: String,
  ): ByteArray {
    val output = ByteArrayOutputStream()
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

  private fun derivedAudibleSourceId(
    title: String,
    authors: List<String>,
    publishedDate: String?,
  ): String {
    val seed = buildString {
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
    val normalizedPath = replace('\\', '/').trim('/')
    val segments = normalizedPath.split('/')
    val name = segments.lastOrNull()?.lowercase(Locale.ROOT).orEmpty()
    val inOwnershipDirectory = segments.dropLast(1).any { segment ->
      segment.equals("Digital.Content.Ownership", ignoreCase = true) ||
        segment.startsWith("Digital.Content.Ownership.", ignoreCase = true)
    }
    return name.endsWith(".json") &&
      (name.startsWith("digital.content.ownership") || inOwnershipDirectory)
  }

  private fun String.isZipFile(): Boolean = baseName().endsWith(".zip")

  private fun String.isAudibleLibraryFile(): Boolean = baseName() == "library.csv"

  private fun String.isExpectedSourceFile(source: LibrarySource): Boolean = when (source) {
    LibrarySource.KINDLE -> isKindleOwnershipFile()
    LibrarySource.AUDIBLE -> isAudibleLibraryFile()
    LibrarySource.GOOGLE_PLAY_BOOKS -> false
  }

  private fun isZip(fileName: String?, bytes: ByteArray): Boolean =
    fileName?.lowercase(Locale.ROOT)?.endsWith(".zip") == true || bytes.hasZipSignature()

  private fun isZip(fileName: String?, input: BufferedInputStream): Boolean {
    if (fileName?.lowercase(Locale.ROOT)?.endsWith(".zip") == true) return true
    input.mark(ZIP_SIGNATURE_SIZE)
    val signature = ByteArray(ZIP_SIGNATURE_SIZE)
    val read = input.read(signature)
    input.reset()
    return read == ZIP_SIGNATURE_SIZE && signature.hasZipSignature()
  }

  private fun ByteArray.hasZipSignature(): Boolean =
    size >= ZIP_SIGNATURE_SIZE && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte() &&
      this[2] == 0x03.toByte() && this[3] == 0x04.toByte()

  private fun InputStream.asBufferedInputStream(): BufferedInputStream =
    this as? BufferedInputStream ?: BufferedInputStream(this)

  private fun LibrarySource.unrecognizedImportMessage(): String = when (this) {
    LibrarySource.KINDLE ->
      "Kindle 蔵書を認識できませんでした。Digital.Content.Ownership*.json またはそれを含む ZIP を選択してください"
    LibrarySource.AUDIBLE ->
      "Audible 蔵書を認識できませんでした。Library.csv またはそれを含む ZIP を選択してください"
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

  private data class KindleZipScanState(
    val candidates: MutableList<KindleOwnershipCandidate> = mutableListOf(),
    var ownershipFileFound: Boolean = false,
    var ordinal: Int = 0,
    var entryCount: Int = 0,
    var expandedOwnershipBytes: Long = 0L,
  )

  private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
  }

  private enum class KindleRightState {
    GRANTED,
    REVOKED,
  }

  private companion object {
    const val MAX_INPUT_BYTES = 25 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 50 * 1024 * 1024L
    const val MAX_ENTRY_BYTES = 25 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 100
    const val MAX_STREAMING_ZIP_ENTRIES = 100_000
    const val MAX_KINDLE_EXPANDED_BYTES = 256 * 1024 * 1024L
    const val MAX_NESTED_ZIP_DEPTH = 4
    const val ZIP_SIGNATURE_SIZE = 4

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

    val KINDLE_ID_HEADERS = listOf(
      "asin",
      "amazonasin",
      "productasin",
      "contentasin",
      "contentid",
      "productid",
      "itemid",
    )
    val KINDLE_TITLE_HEADERS = listOf(
      "productname",
      "title",
      "booktitle",
      "producttitle",
      "contenttitle",
      "itemtitle",
      "name",
    )
    val KINDLE_AUTHOR_HEADERS = listOf("author", "authors", "creator", "creators", "writtenby")
    val KINDLE_PUBLISHED_DATE_HEADERS = listOf(
      "publisheddate",
      "publicationdate",
      "releasedate",
      "releasedatetime",
    )
    val KINDLE_EVENT_DATE_HEADERS = listOf(
      "lastupdateddate",
      "acquireddate",
      "eventtimestamp",
      "timestamp",
      "updatedat",
      "createdat",
      "acquisitiondate",
      "purchasedate",
      "date",
    )
    val KINDLE_RIGHT_STATUS_HEADERS = listOf("rightstatus")
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
      "resourcetype",
      "contenttype",
      "digitalcontenttype",
      "producttype",
      "mediatype",
      "assettype",
      "format",
    )
    val KINDLE_ORIGIN_TYPE_HEADERS = listOf("origintype")
    val KINDLE_ACTIVE_STATUS_MARKERS = listOf("active", "enabled", "current", "valid")
    val KINDLE_INACTIVE_STATUS_MARKERS = listOf("inactive", "revoked", "expired", "deleted", "removed")
    val KINDLE_REVOKED_MARKERS = listOf("revoke", "return", "expire", "delete", "remove")
    val KINDLE_GRANTED_MARKERS = listOf("grant", "purchase", "acquire", "own")
    val KINDLE_NON_BOOK_TYPE_MARKERS = listOf("music", "song", "album", "video", "movie", "audible", "audiobook")
    val KINDLE_SYSTEM_CONTENT_ORIGIN_MARKERS = listOf(
      "kindledictionary",
      "kindleuserguide",
      "kindledeviceguide",
      "kindledevicemanual",
    )
    val KINDLE_SYSTEM_GUIDE_TITLE_MARKERS = listOf(
      "user's guide",
      "user’s guide",
      "users guide",
      "user guide",
      "ユーザーガイド",
      "ユーザガイド",
      "取扱説明書",
    )
  }
}
