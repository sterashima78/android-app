package dev.terashima.yomitorirss.feature.library

internal data class LibraryMetadataBookGroup(
  val id: String,
  val name: String,
  val books: List<LibraryBook>,
)

internal fun libraryTagGroups(
  books: List<LibraryBook>,
  snapshot: LibraryOrganizationSnapshot,
): List<LibraryMetadataBookGroup> = snapshot.tags.mapNotNull { tag ->
  val members = books.membersOf { book ->
    snapshot.organizationFor(book).tags.any { it.id == tag.id }
  }
  members.takeIf(List<LibraryBook>::isNotEmpty)?.let {
    LibraryMetadataBookGroup(tag.id, tag.name, it)
  }
}.sortedBy { it.name.lowercase() }

internal fun libraryCollectionGroups(
  books: List<LibraryBook>,
  snapshot: LibraryOrganizationSnapshot,
): List<LibraryMetadataBookGroup> = snapshot.collections.mapNotNull { collection ->
  val members = books.membersOf { book ->
    snapshot.organizationFor(book).collections.any { it.id == collection.id }
  }
  members.takeIf(List<LibraryBook>::isNotEmpty)?.let {
    LibraryMetadataBookGroup(collection.id, collection.name, it)
  }
}.sortedBy { it.name.lowercase() }

internal fun LibraryItemOrganization.withoutTag(tagId: String): LibraryOrganizationDraft =
  LibraryOrganizationDraft(
    tagNames = tags.filterNot { it.id == tagId }.map(LibraryOrganizationTag::name),
    collectionNames = collections.map(LibraryCollection::name),
    readingStatus = readingStatus,
  )

internal fun LibraryItemOrganization.withoutCollection(collectionId: String): LibraryOrganizationDraft =
  LibraryOrganizationDraft(
    tagNames = tags.map(LibraryOrganizationTag::name),
    collectionNames = collections
      .filterNot { it.id == collectionId }
      .map(LibraryCollection::name),
    readingStatus = readingStatus,
  )

private fun List<LibraryBook>.membersOf(predicate: (LibraryBook) -> Boolean): List<LibraryBook> =
  filter(predicate)
    .sortedWith(compareBy<LibraryBook> { it.title.lowercase() }.thenBy { it.sourceId })
