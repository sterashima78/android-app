package dev.terashima.yomitorirss.feature.library

internal data class LibrarySeriesSection(
  val key: String,
  val name: String,
  val books: List<LibraryBook>,
)

internal data class LibraryBookGroups(
  val series: List<LibrarySeriesSection>,
  val ungrouped: List<LibraryBook>,
)

internal data class LibrarySeriesMergeAssignment(
  val book: LibraryBook,
  val series: LibrarySeries,
)

private val parenthesizedSeriesPosition = Regex(
  """^(.*?)[\s　]*[\(（][\s　]*([0-9０-９]+)[\s　]*[\)）][\s　]*$""",
)
private val volumeSuffixSeriesPosition = Regex(
  """^(.*?)[\s　]*([0-9０-９]+)[\s　]*巻[\s　]*$""",
)
private val trailingSeriesPosition = Regex(
  """^(.*?)([0-9０-９]+)[\s　]*$""",
)

internal fun inferLibrarySeriesFromTitle(title: String): LibrarySeries? {
  val match = parenthesizedSeriesPosition.matchEntire(title.trim())
    ?: volumeSuffixSeriesPosition.matchEntire(title.trim())
    ?: trailingSeriesPosition.matchEntire(title.trim())
    ?: return null
  val seriesName = match.groupValues[1]
    .trim()
    .trimEnd('-', '‐', '‑', '–', '—', '#', '＃', ':', '：')
    .trim()
  if (seriesName.isEmpty()) return null

  val position = normalizeDigits(match.groupValues[2])
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: return null
  return LibrarySeries(
    name = seriesName,
    position = position,
  )
}

internal fun groupLibraryBooks(books: List<LibraryBook>): LibraryBookGroups {
  val effectiveBooks = books.map { book ->
    if (book.series != null || book.automaticSeriesExcluded) {
      book
    } else {
      inferLibrarySeriesFromTitle(book.title)?.let { inferred ->
        book.copy(series = inferred)
      } ?: book
    }
  }

  val assigned = effectiveBooks.filter { !it.series?.name.isNullOrBlank() }
  val ungrouped = effectiveBooks
    .filter { it.series?.name.isNullOrBlank() }
    .sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })

  val series = assigned
    .groupBy { book -> seriesKey(requireNotNull(book.series)) }
    .map { (key, seriesBooks) ->
      LibrarySeriesSection(
        key = key,
        name = requireNotNull(seriesBooks.first().series).name.trim(),
        books = seriesBooks.sortedWith(
          compareBy<LibraryBook> { it.series?.position ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() }
            .thenBy { it.sourceId },
        ),
      )
    }
    .sortedBy { it.name.lowercase() }

  return LibraryBookGroups(
    series = series,
    ungrouped = ungrouped,
  )
}

internal fun mergeLibrarySeries(
  source: LibrarySeriesSection,
  target: LibrarySeriesSection,
): List<LibrarySeriesMergeAssignment> {
  require(source.key != target.key) { "同じシリーズにはマージできません" }
  val targetName = target.name.trim()
  require(targetName.isNotEmpty()) { "マージ先のシリーズ名がありません" }

  return (target.books + source.books).map { book ->
    LibrarySeriesMergeAssignment(
      book = book,
      series = LibrarySeries(
        name = targetName,
        position = book.series?.position,
      ),
    )
  }
}

private fun seriesKey(series: LibrarySeries): String =
  series.id?.trim()?.takeIf(String::isNotEmpty)?.let { "id:${it.uppercase()}" }
    ?: "name:${series.name.trim().lowercase()}"

private fun normalizeDigits(value: String): String = buildString(value.length) {
  value.forEach { character ->
    append(
      if (character in '０'..'９') {
        ('0'.code + character.code - '０'.code).toChar()
      } else {
        character
      },
    )
  }
}
