package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import java.io.ByteArrayInputStream
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
    if (source != LibrarySource.AUDIBLE || fileName?.isAudibleWebLibraryJson() != true) {
      return delegate.importAmazonLibrary(source, fileName, input)
    }

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

private fun List<LibraryBook>.toAudibleLibraryCsv(): String = buildString {
  appendLine(
    "ASIN,Title,Authors,Publisher,Published Date,Description,Cover URL,Product URL,Narrators,Duration",
  )
  forEach { book ->
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

private fun String.csvField(): String = if (
  contains(',') || contains('"') || contains('\n') || contains('\r')
) {
  "\"${replace("\"", "\"\"")}\""
} else {
  this
}
