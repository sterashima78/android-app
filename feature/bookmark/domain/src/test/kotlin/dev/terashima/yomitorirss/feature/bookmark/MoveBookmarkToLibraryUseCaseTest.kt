package dev.terashima.yomitorirss.feature.bookmark

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryAdder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MoveBookmarkToLibraryUseCaseTest {
  @Test
  fun `ブックマークから蔵書への移動は追加後にブックマークを解除する`() = runBlocking {
    val events = mutableListOf<String>()
    val useCase = useCase(events)

    useCase(article())

    assertEquals(listOf("library:add", "bookmark:unsave", "bookmark:changed"), events)
  }

  @Test
  fun `蔵書追加に失敗した場合はブックマークを残す`() = runBlocking {
    val events = mutableListOf<String>()
    val useCase = useCase(events, failLibraryAdd = true)

    try {
      useCase(article())
      fail("蔵書追加失敗を通知する必要があります")
    } catch (_: IllegalStateException) {
      // Expected: source bookmark must remain untouched.
    }

    assertEquals(listOf("library:add"), events)
  }

  private fun useCase(
    events: MutableList<String>,
    failLibraryAdd: Boolean = false,
  ): MoveBookmarkToLibraryUseCase {
    val webLibrary = object : WebLibraryAdder {
      override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
        events += "library:add"
        if (failLibraryAdd) error("metadata fetch failed")
        return webBook()
      }
    }
    val bookmarkMutator = object : BookmarkMutator {
      override suspend fun moveArticleToFolder(articleId: String, folderId: String?) = Unit
      override suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>) = Unit
      override suspend fun saveAndReadArticle(articleId: String) = Unit
      override suspend fun markReadLater(articleId: String) = Unit
      override suspend fun unsaveArticle(articleId: String) {
        events += "bookmark:unsave"
      }
      override suspend fun removeReadLater(articleId: String) = Unit
    }
    return MoveBookmarkToLibraryUseCase(
      webLibrary = webLibrary,
      bookmarkMutator = bookmarkMutator,
      onBookmarkChanged = { events += "bookmark:changed" },
    )
  }

  private fun article() = Article(
    id = "article-1",
    feedId = null,
    externalId = null,
    identityKey = "https://example.com/book",
    url = "https://example.com/book",
    title = "Book",
    publishedAt = "2026-08-22T00:00:00Z",
    fetchedAt = "2026-08-22T00:00:00Z",
    readAt = null,
    sourceTitle = "example.com",
    sourceFeedUrl = "",
  )

  private fun webBook() = LibraryBook(
    source = LibrarySource.WEB,
    sourceId = "https://example.com/book",
    title = "Book",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = "https://example.com/cover.jpg",
    infoUrl = "https://example.com/book",
  )
}
