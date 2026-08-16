package dev.terashima.yomitorirss.feature.library

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryMetadataOrganizerTest {
  @Test
  fun `シリーズ再整理はAI解析に成功した書籍だけ保存する`() {
    val first = book("book-1", "シリーズ 1", LibrarySeries("シリーズ", 1, "series-1"))
    val second = book("book-2", "シリーズ 2", LibrarySeries("シリーズ", 2, "series-1"))
    val firstOrganization = LibraryItemOrganization(
      key = first.organizationKey(),
      tags = listOf(tag("old")),
      collections = listOf(collection("既存")),
      readingStatus = LibraryReadingStatus.READING,
    )
    val secondOrganization = LibraryItemOrganization(
      key = second.organizationKey(),
      tags = listOf(tag("keep")),
      collections = listOf(collection("保持")),
      readingStatus = LibraryReadingStatus.FINISHED,
    )
    val repository = FakeOrganizationRepository(
      LibraryOrganizationSnapshot(
        tags = listOf(tag("old"), tag("keep")),
        collections = listOf(collection("既存"), collection("保持")),
        items = mapOf(
          first.organizationKey() to firstOrganization,
          second.organizationKey() to secondOrganization,
        ),
      ),
    )
    val suggester = object : LibraryOrganizationSuggester {
      override suspend fun suggest(
        book: LibraryBook,
        existingTags: List<String>,
        existingCollections: List<String>,
        seriesContext: LibraryOrganizationSeriesContext?,
      ): LibraryOrganizationSuggestion {
        if (book.sourceId == second.sourceId) error("解析失敗")
        return LibraryOrganizationSuggestion(
          tagNames = listOf("new"),
          collectionNames = listOf("新規"),
          reason = null,
        )
      }
    }

    val result = runSuspend {
      LibraryMetadataOrganizer(repository, suggester).reorganizeSeries(listOf(first, second))
    }

    assertEquals(LibrarySeriesReorganizationResult(total = 2, updated = 1, failed = 1), result)
    assertEquals(1, repository.saved.size)
    assertEquals(first.organizationKey(), repository.saved.single().first.organizationKey())
    assertEquals(listOf("new"), repository.saved.single().second.tagNames)
    assertEquals(listOf("新規"), repository.saved.single().second.collectionNames)
    assertEquals(LibraryReadingStatus.READING, repository.saved.single().second.readingStatus)
  }

  @Test
  fun `シリーズコンテキストは同一シリーズの既存分類だけを集める`() {
    val target = book("book-1", "シリーズ 1", LibrarySeries("シリーズ", 1, "series-1"))
    val peer = book("book-2", "シリーズ 2", LibrarySeries("シリーズ", 2, "series-1"))
    val other = book("book-3", "別シリーズ 1", LibrarySeries("別シリーズ", 1, "series-2"))
    val snapshot = LibraryOrganizationSnapshot(
      items = mapOf(
        peer.organizationKey() to LibraryItemOrganization(
          key = peer.organizationKey(),
          tags = listOf(tag("共通タグ")),
          collections = listOf(collection("共通コレクション")),
        ),
        other.organizationKey() to LibraryItemOrganization(
          key = other.organizationKey(),
          tags = listOf(tag("別タグ")),
          collections = listOf(collection("別コレクション")),
        ),
      ),
    )

    val context = seriesContextForMetadataReorganization(
      book = target,
      books = listOf(target, peer, other),
      snapshot = snapshot,
    )

    assertEquals(listOf("共通タグ"), context?.tagNames)
    assertEquals(listOf("共通コレクション"), context?.collectionNames)
  }

  @Test
  fun `シリーズ分類がなければコンテキストを作らない`() {
    val target = book("book-1", "単巻", null)

    assertNull(
      seriesContextForMetadataReorganization(
        book = target,
        books = listOf(target),
        snapshot = LibraryOrganizationSnapshot(),
      ),
    )
  }

  private fun book(
    id: String,
    title: String,
    series: LibrarySeries?,
  ): LibraryBook = LibraryBook(
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
    series = series,
  )

  private fun tag(name: String) = LibraryOrganizationTag(
    id = "tag-$name",
    name = name,
    normalizedName = name.lowercase(),
  )

  private fun collection(name: String) = LibraryCollection(
    id = "collection-$name",
    name = name,
    normalizedName = name.lowercase(),
  )
}

private class FakeOrganizationRepository(
  private val currentSnapshot: LibraryOrganizationSnapshot,
) : LibraryOrganizationRepository {
  val saved = mutableListOf<Pair<LibraryBook, LibraryOrganizationDraft>>()

  override suspend fun snapshot(): LibraryOrganizationSnapshot = currentSnapshot

  override suspend fun save(book: LibraryBook, draft: LibraryOrganizationDraft) {
    saved += book to draft
  }

  override suspend fun batchSnapshot(): LibraryOrganizationBatchSnapshot? = null

  override suspend fun startBatch(books: List<LibraryBook>): String = error("not used")

  override suspend fun pauseBatch() = error("not used")

  override suspend fun resumeBatch() = error("not used")

  override suspend fun updateCandidate(key: LibraryBookKey, draft: LibraryOrganizationDraft) =
    error("not used")

  override suspend fun acceptCandidate(book: LibraryBook, draft: LibraryOrganizationDraft) =
    error("not used")

  override suspend fun deferCandidate(key: LibraryBookKey) = error("not used")

  override suspend fun rejectCandidate(key: LibraryBookKey) = error("not used")

  override suspend fun reopenCandidate(key: LibraryBookKey) = error("not used")

  override suspend fun retryCandidate(key: LibraryBookKey) = error("not used")
}

private fun <T> runSuspend(block: suspend () -> T): T {
  var completed: Result<T>? = null
  block.startCoroutine(
    object : Continuation<T> {
      override val context = EmptyCoroutineContext

      override fun resumeWith(result: Result<T>) {
        completed = result
      }
    },
  )
  return requireNotNull(completed) { "suspend block did not complete synchronously" }.getOrThrow()
}
