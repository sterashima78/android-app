package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMetadataManagementTest {
  @Test
  fun `タグごとに所属蔵書を名前順で一覧化する`() {
    val first = book("book-1", "Zeta Book")
    val second = book("book-2", "Alpha Book")
    val android = tag("tag-android", "Android")
    val kotlin = tag("tag-kotlin", "Kotlin")
    val unused = tag("tag-unused", "Unused")
    val snapshot = LibraryOrganizationSnapshot(
      tags = listOf(kotlin, unused, android),
      items = mapOf(
        first.organizationKey() to organization(first, tags = listOf(android)),
        second.organizationKey() to organization(second, tags = listOf(android, kotlin)),
      ),
    )

    val groups = libraryTagGroups(listOf(first, second), snapshot)

    assertEquals(listOf("Android", "Kotlin"), groups.map(LibraryMetadataBookGroup::name))
    assertEquals(listOf("Alpha Book", "Zeta Book"), groups.first().books.map(LibraryBook::title))
    assertEquals(listOf("Alpha Book"), groups.last().books.map(LibraryBook::title))
  }

  @Test
  fun `コレクションから外してもタグと読書状態を保持する`() {
    val book = book("book-1", "Example Book")
    val android = tag("tag-android", "Android")
    val technical = collection("collection-technical", "Technical")
    val reference = collection("collection-reference", "Reference")
    val organization = organization(
      book = book,
      tags = listOf(android),
      collections = listOf(technical, reference),
      readingStatus = LibraryReadingStatus.READING,
    )

    val draft = organization.withoutCollection(technical.id)

    assertEquals(listOf("Android"), draft.tagNames)
    assertEquals(listOf("Reference"), draft.collectionNames)
    assertEquals(LibraryReadingStatus.READING, draft.readingStatus)
  }

  @Test
  fun `タグから外してもコレクションと読書状態を保持する`() {
    val book = book("book-1", "Example Book")
    val android = tag("tag-android", "Android")
    val kotlin = tag("tag-kotlin", "Kotlin")
    val technical = collection("collection-technical", "Technical")
    val organization = organization(
      book = book,
      tags = listOf(android, kotlin),
      collections = listOf(technical),
      readingStatus = LibraryReadingStatus.FINISHED,
    )

    val draft = organization.withoutTag(android.id)

    assertEquals(listOf("Kotlin"), draft.tagNames)
    assertEquals(listOf("Technical"), draft.collectionNames)
    assertEquals(LibraryReadingStatus.FINISHED, draft.readingStatus)
  }

  private fun book(id: String, title: String) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = id,
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private fun tag(id: String, name: String) = LibraryOrganizationTag(
    id = id,
    name = name,
    normalizedName = name.lowercase(),
  )

  private fun collection(id: String, name: String) = LibraryCollection(
    id = id,
    name = name,
    normalizedName = name.lowercase(),
  )

  private fun organization(
    book: LibraryBook,
    tags: List<LibraryOrganizationTag> = emptyList(),
    collections: List<LibraryCollection> = emptyList(),
    readingStatus: LibraryReadingStatus? = null,
  ) = LibraryItemOrganization(
    key = book.organizationKey(),
    tags = tags,
    collections = collections,
    readingStatus = readingStatus,
  )
}
