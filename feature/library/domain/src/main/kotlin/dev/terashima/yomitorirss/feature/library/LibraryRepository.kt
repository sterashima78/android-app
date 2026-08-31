package dev.terashima.yomitorirss.feature.library

interface LibraryReader {
  suspend fun snapshot(): LibrarySnapshot
}

interface LibraryRepository : LibraryReader {
  suspend fun hideBook(book: LibraryBook)

  suspend fun restoreBook(book: LibraryBook)

  suspend fun setBookSeries(
    book: LibraryBook,
    series: LibrarySeries,
  )

  suspend fun setBookSeries(updates: List<LibraryBookSeriesUpdate>) {
    updates.forEach { update -> setBookSeries(update.book, update.series) }
  }

  suspend fun clearBookSeries(book: LibraryBook)

  suspend fun syncGooglePlayBooks(
    accessToken: String,
    accountLabel: String?,
  ): LibrarySyncResult

  suspend fun importAmazonLibraryJson(
    source: LibrarySource,
    json: String,
  ): LibrarySyncResult
}
