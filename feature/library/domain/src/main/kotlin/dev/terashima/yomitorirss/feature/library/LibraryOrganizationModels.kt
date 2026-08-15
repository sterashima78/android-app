package dev.terashima.yomitorirss.feature.library

enum class LibraryReadingStatus(val label: String) {
  UNREAD("未読"),
  READING("読書中"),
  FINISHED("読了"),
  PAUSED("中断中"),
  ABANDONED("中止"),
}

data class LibraryBookKey(
  val source: LibrarySource,
  val sourceId: String,
)

data class LibraryOrganizationTag(
  val id: String,
  val name: String,
  val normalizedName: String,
)

data class LibraryCollection(
  val id: String,
  val name: String,
  val normalizedName: String,
)

data class LibraryItemOrganization(
  val key: LibraryBookKey,
  val tags: List<LibraryOrganizationTag> = emptyList(),
  val collections: List<LibraryCollection> = emptyList(),
  val readingStatus: LibraryReadingStatus? = null,
)

data class LibraryOrganizationSnapshot(
  val tags: List<LibraryOrganizationTag> = emptyList(),
  val collections: List<LibraryCollection> = emptyList(),
  val items: Map<LibraryBookKey, LibraryItemOrganization> = emptyMap(),
) {
  fun organizationFor(book: LibraryBook): LibraryItemOrganization =
    items[book.organizationKey()] ?: LibraryItemOrganization(book.organizationKey())
}

data class LibraryOrganizationDraft(
  val tagNames: List<String>,
  val collectionNames: List<String>,
  val readingStatus: LibraryReadingStatus?,
)

data class LibraryOrganizationUpdate(
  val book: LibraryBook,
  val draft: LibraryOrganizationDraft,
)

data class LibraryOrganizationSuggestion(
  val tagNames: List<String>,
  val collectionNames: List<String>,
  val reason: String?,
)

fun LibraryBook.organizationKey(): LibraryBookKey = LibraryBookKey(source, sourceId)

interface LibraryOrganizationRepository {
  suspend fun snapshot(): LibraryOrganizationSnapshot

  suspend fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  )

  suspend fun saveAll(updates: List<LibraryOrganizationUpdate>) {
    updates.forEach { update -> save(update.book, update.draft) }
  }
}

interface LibraryOrganizationSuggester {
  suspend fun suggest(
    book: LibraryBook,
    existingTags: List<String>,
    existingCollections: List<String>,
  ): LibraryOrganizationSuggestion
}
