package dev.terashima.yomitorirss.composition.entry

import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.library.LibraryBook

class SharedContentEntryCapability internal constructor(
  private val saveSharedBookmark: SaveSharedBookmarkUseCase,
  private val addSharedWebBook: suspend (String, String?) -> LibraryBook,
) {
  suspend fun saveBookmark(
    url: String,
    title: String,
    sourceTitle: String,
  ): SharedBookmarkSaveOutcome = when (saveSharedBookmark(url, title, sourceTitle)) {
    BookmarkSaveResult.ADDED -> SharedBookmarkSaveOutcome.ADDED
    BookmarkSaveResult.ALREADY_BOOKMARKED -> SharedBookmarkSaveOutcome.ALREADY_BOOKMARKED
  }

  suspend fun addWebBook(
    url: String,
    title: String,
  ): AddedSharedWebBook = AddedSharedWebBook(
    title = addSharedWebBook(url, title).title,
  )
}

enum class SharedBookmarkSaveOutcome {
  ADDED,
  ALREADY_BOOKMARKED,
}

data class AddedSharedWebBook(
  val title: String,
)
