package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.library.LibraryBook

class IncomingIntentDependencies internal constructor(
  private val saveSharedBookmark: SaveSharedBookmarkUseCase,
  private val addSharedWebBookCapability: suspend (String, String?) -> LibraryBook,
) {
  suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult = saveSharedBookmark(url, title, sourceTitle)

  suspend fun addSharedWebBook(
    url: String,
    title: String,
  ): LibraryBook = addSharedWebBookCapability(url, title)
}
