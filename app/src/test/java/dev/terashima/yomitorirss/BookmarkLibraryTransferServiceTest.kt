package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkMutator
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.bookmark.SharedBookmarkSaver
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BookmarkLibraryTransferServiceTest {
  @Test
  fun `ブックマークから蔵書への移動は追加後にブックマークを解除する`() = runBlocking {
    val events = mutableListOf<String>()
    val service = service(events = events)

    service.moveBookmarkToLibrary(article())

    assertEquals(listOf("library:add", "bookmark:unsave", "changed"), events)
  }

  @Test
  fun `蔵書追加に失敗した場合はブックマークを残す`() = runBlocking {
    val events = mutableListOf<String>()
    val service = service(events = events, failLibraryAdd = true)

    try {
      service.moveBookmarkToLibrary(article())
      fail("蔵書追加失敗を通知する必要があります")
    } catch (_: IllegalStateException) {
      // Expected: source bookmark must remain untouched.
    }

    assertEquals(listOf("library:add"), events)
  }

  @Test
  fun `Web蔵書からブックマークへの移動は保存後に蔵書を削除する`() = runBlocking {
    val events = mutableListOf<String>()
    val service = service(events = events)

    service.moveWebBookToBookmark(webBook())

    assertEquals(listOf("bookmark:save", "library:remove", "changed"), events)
  }

  @Test
  fun `ブックマーク保存に失敗した場合はWeb蔵書を残す`() = runBlocking {
    val events = mutableListOf<String>()
    val service = service(events = events, failBookmarkSave = true)

    try {
      service.moveWebBookToBookmark(webBook())
      fail("ブックマーク保存失敗を通知する必要があります")
    } catch (_: IllegalStateException) {
      // Expected: source Web library item must remain untouched.
    }

    assertEquals(listOf("bookmark:save"), events)
  }

  @Test
  fun `Web蔵書再取得はdelegateのrefreshを呼びバックアップ変更を通知する`() = runBlocking {
    val events = mutableListOf<String>()
    val delegate = object : WebLibraryMutator {
      override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
        events += "library:add"
        return webBook()
      }

      override suspend fun refreshWebBook(book: LibraryBook): LibraryBook {
        events += "library:refresh"
        return book.copy(title = "Refreshed")
      }

      override suspend fun removeWebBook(book: LibraryBook) = Unit
    }
    val notifying = NotifyingWebLibraryMutator(delegate) { events += "changed" }

    val refreshed = notifying.refreshWebBook(webBook())

    assertEquals("Refreshed", refreshed.title)
    assertEquals(listOf("library:refresh", "changed"), events)
  }

  private fun service(
    events: MutableList<String>,
    failLibraryAdd: Boolean = false,
    failBookmarkSave: Boolean = false,
  ): BookmarkLibraryTransferService {
    val webLibrary = object : WebLibraryMutator {
      override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
        events += "library:add"
        if (failLibraryAdd) error("metadata fetch failed")
        return webBook()
      }

      override suspend fun removeWebBook(book: LibraryBook) {
        events += "library:remove"
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
    val sharedSaver = object : SharedBookmarkSaver {
      override suspend fun saveSharedArticle(
        url: String,
        title: String,
        sourceTitle: String,
      ): BookmarkSaveResult {
        events += "bookmark:save"
        if (failBookmarkSave) error("bookmark save failed")
        return BookmarkSaveResult.ADDED
      }

      override suspend fun saveSharedArticleToFolder(
        url: String,
        title: String,
        sourceTitle: String,
        folderId: String,
      ): BookmarkSaveResult = BookmarkSaveResult.ADDED
    }
    return BookmarkLibraryTransferService(
      webLibrary = webLibrary,
      bookmarkMutator = bookmarkMutator,
      saveSharedBookmark = SaveSharedBookmarkUseCase(sharedSaver),
      onChanged = { events += "changed" },
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
