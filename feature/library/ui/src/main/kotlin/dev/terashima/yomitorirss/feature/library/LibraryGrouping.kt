package dev.terashima.yomitorirss.feature.library

internal data class LibrarySeriesSection(
  val name: String,
  val books: List<LibraryBook>,
)

internal data class LibraryBookGroups(
  val series: List<LibrarySeriesSection>,
  val ungrouped: List<LibraryBook>,
)

internal fun groupLibraryBooks(books: List<LibraryBook>): LibraryBookGroups {
  val assigned = books.filter { !it.series?.name.isNullOrBlank() }
  val ungrouped = books
    .filter { it.series?.name.isNullOrBlank() }
    .sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })

  val series = assigned
    .groupBy { requireNotNull(it.series).name.trim() }
    .map { (name, seriesBooks) ->
      LibrarySeriesSection(
        name = name,
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
