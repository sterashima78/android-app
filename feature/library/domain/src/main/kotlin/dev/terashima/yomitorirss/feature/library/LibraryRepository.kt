package dev.terashima.yomitorirss.feature.library

interface LibraryRepository {
  suspend fun snapshot(): LibrarySnapshot

  suspend fun hideBook(book: LibraryBook)

  suspend fun restoreBook(book: LibraryBook)

  suspend fun setBookSeries(
    book: LibraryBook,
    series: LibrarySeries,
  )

  suspend fun clearBookSeries(book: LibraryBook)

  suspend fun syncGooglePlayBooks(
    accessToken: String,
    accountLabel: String?,
  ): LibrarySyncResult

  suspend fun importAmazonLibrary(
    source: LibrarySource,
    fileName: String?,
    bytes: ByteArray,
  ): LibrarySyncResult
}
