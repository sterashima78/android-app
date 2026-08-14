package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal class AudibleWebAwareLibraryRepository(
  private val delegate: LibraryRepository,
) : LibraryRepository by delegate {
  override suspend fun importAmazonLibrary(
    source: LibrarySource,
    fileName: String?,
    input: InputStream,
  ): LibrarySyncResult {
    if (source != LibrarySource.AUDIBLE) {
      return delegate.importAmazonLibrary(source, fileName, input)
    }
    if (fileName?.isAudibleWebLibraryJson() == true) {
      return importWebJson(fileName, input)
    }
    if (fileName != null) {
      return delegate.importAmazonLibrary(source, fileName, input)
    }

    val bytes = input.readLimited(MAX_AUDIBLE_INPUT_BYTES)
    return if (bytes.looksLikeJson()) {
      ByteArrayInputStream(bytes).use { importWebJson(fileName = null, input = it) }
    } else {
      val inferredName = if (bytes.hasZipSignature()) "Library.zip" else "Library.csv"
      ByteArrayInputStream(bytes).use { delegate.importAmazonLibrary(source, inferredName, it) }
    }
  }

  private suspend fun importWebJson(
    fileName: String?,
    input: InputStream,
  ): LibrarySyncResult {
    val books = AudibleWebLibraryImporter().parse(fileName, input)
    val csv = books.toAudibleLibraryCsv().toByteArray(StandardCharsets.UTF_8)
    return ByteArrayInputStream(csv).use { syntheticInput ->
      delegate.importAmazonLibrary(
        source = LibrarySource.AUDIBLE,
        fileName = "Library.csv",
        input = syntheticInput,
      )
    }
  }
}

private fun List<LibraryBook>.toAudibleLibraryCsv(): String {
  val books = this
  return buildString {
    appendLine(
      "ASIN,Title,Authors,Publisher,Published Date,Description,Cover URL,Product URL,Narrators,Duration",
    )
    books.forEach { book ->
      appendLine(
        listOf(
          book.sourceId,
          book.title,
          book.authors.joinToString(";"),
          book.publisher.orEmpty(),
          book.publishedDate.orEmpty(),
          book.description.orEmpty(),
          book.thumbnailUrl.orEmpty(),
          book.infoUrl.orEmpty(),
          book.narrators.joinToString(";"),
          book.duration.orEmpty(),
        ).joinToString(",") { it.csvField() },
      )
    }
  }
}

private fun String.csvField(): String = if (
  contains(',') || contains('"') || contains('\n') || contains('\r')
) {
  "\"${replace("\"", "\"\"")}\""
} else {
  this
}

private fun InputStream.readLimited(limit: Int): ByteArray {
  val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var total = 0
  while (true) {
    val read = read(buffer)
    if (read < 0) break
    total += read
    require(total <= limit) { "Audible インポートファイルが大きすぎます（上限 25 MB）" }
    output.write(buffer, 0, read)
  }
  return output.toByteArray()
}

private fun ByteArray.looksLikeJson(): Boolean {
  val text = toString(StandardCharsets.UTF_8).removePrefix("\uFEFF").trimStart()
  return text.startsWith('{') || text.startsWith('[')
}

private fun ByteArray.hasZipSignature(): Boolean =
  size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte() &&
    this[2] == 0x03.toByte() && this[3] == 0x04.toByte()

private const val MAX_AUDIBLE_INPUT_BYTES = 25 * 1024 * 1024
