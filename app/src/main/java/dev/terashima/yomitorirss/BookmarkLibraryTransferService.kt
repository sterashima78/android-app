package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkMutator
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator

internal class NotifyingWebLibraryMutator(
  private val delegate: WebLibraryMutator,
  private val onChanged: () -> Unit,
) : WebLibraryMutator {
  override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook =
    delegate.addWebBook(url, titleHint).also { onChanged() }

  override suspend fun refreshWebBook(book: LibraryBook): LibraryBook =
    delegate.refreshWebBook(book).also { onChanged() }

  override suspend fun removeWebBook(book: LibraryBook) {
    delegate.removeWebBook(book)
    onChanged()
  }
}

internal class BookmarkLibraryTransferService(
  private val webLibrary: WebLibraryMutator,
  private val bookmarkMutator: BookmarkMutator,
  private val saveSharedBookmark: SaveSharedBookmarkUseCase,
  private val onChanged: () -> Unit,
) {
  suspend fun moveBookmarkToLibrary(article: Article) {
    webLibrary.addWebBook(article.url, article.title)
    bookmarkMutator.unsaveArticle(article.id)
    onChanged()
  }

  suspend fun moveWebBookToBookmark(book: LibraryBook) {
    require(book.source == LibrarySource.WEB) { "Web 蔵書のみブックマークへ移動できます" }
    val url = book.infoUrl?.takeIf(String::isNotBlank) ?: book.sourceId
    saveSharedBookmark(url, book.title, "Web")
    webLibrary.removeWebBook(book)
    onChanged()
  }
}
