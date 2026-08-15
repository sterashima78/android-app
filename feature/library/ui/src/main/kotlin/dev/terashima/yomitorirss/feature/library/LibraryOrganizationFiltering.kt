package dev.terashima.yomitorirss.feature.library

enum class LibraryOrganizationFilter(val label: String) {
  ALL("すべて"),
  UNORGANIZED("未整理"),
  STATUS_UNSET("状態未設定"),
  UNREAD("未読"),
  READING("読書中"),
  FINISHED("読了"),
}

internal fun filterLibraryBooksForOrganization(
  books: List<LibraryBook>,
  snapshot: LibraryOrganizationSnapshot,
  filter: LibraryOrganizationFilter,
): List<LibraryBook> = books.filter { book ->
  val organization = snapshot.organizationFor(book)
  when (filter) {
    LibraryOrganizationFilter.ALL -> true
    LibraryOrganizationFilter.UNORGANIZED ->
      organization.tags.isEmpty() && organization.collections.isEmpty()
    LibraryOrganizationFilter.STATUS_UNSET -> organization.readingStatus == null
    LibraryOrganizationFilter.UNREAD -> organization.readingStatus == LibraryReadingStatus.UNREAD
    LibraryOrganizationFilter.READING -> organization.readingStatus == LibraryReadingStatus.READING
    LibraryOrganizationFilter.FINISHED -> organization.readingStatus == LibraryReadingStatus.FINISHED
  }
}
