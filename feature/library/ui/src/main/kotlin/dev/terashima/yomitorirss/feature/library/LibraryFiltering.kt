package dev.terashima.yomitorirss.feature.library

internal fun filterLibraryBooksBySource(
  books: List<LibraryBook>,
  source: LibrarySource?,
): List<LibraryBook> = if (source == null) {
  books
} else {
  books.filter { it.source == source }
}

internal fun filterLibraryBooksByText(
  books: List<LibraryBook>,
  query: String,
): List<LibraryBook> {
  val terms = query.trim()
    .split(LIBRARY_SEARCH_SEPARATOR)
    .filter(String::isNotEmpty)
  if (terms.isEmpty()) return books

  return books.filter { book ->
    val searchableValues = buildList {
      add(book.title)
      addAll(book.authors)
      book.publisher?.let(::add)
      book.publishedDate?.let(::add)
      book.series?.name?.let(::add)
      addAll(book.narrators)
      book.isbn10?.let(::add)
      book.isbn13?.let(::add)
      add(book.sourceId)
    }
    terms.all { term ->
      searchableValues.any { value -> value.contains(term, ignoreCase = true) }
    }
  }
}

private val LIBRARY_SEARCH_SEPARATOR = Regex("\\s+")
