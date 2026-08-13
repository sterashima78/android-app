package dev.terashima.yomitorirss.feature.library

internal fun filterLibraryBooksBySource(
  books: List<LibraryBook>,
  source: LibrarySource?,
): List<LibraryBook> = if (source == null) {
  books
} else {
  books.filter { it.source == source }
}
