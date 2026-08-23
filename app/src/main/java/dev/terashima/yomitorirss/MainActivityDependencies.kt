package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.web.LanWebServerController

interface MainActivityDependenciesProvider {
  val mainActivityDependencies: MainActivityDependencies
}

class MainActivityDependencies internal constructor(
  val routeDependencies: AppRouteDependencies,
  val lanWebServerController: LanWebServerController,
  private val saveSharedBookmark: SaveSharedBookmarkUseCase,
) {
  suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult = saveSharedBookmark(url, title, sourceTitle)

  suspend fun addSharedWebBook(
    url: String,
    title: String,
  ): LibraryBook = routeDependencies.library.addWebBook(url, title)
}
