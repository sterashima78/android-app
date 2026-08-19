package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase

interface MainActivityDependenciesProvider {
  val mainActivityDependencies: MainActivityDependencies
}

class MainActivityDependencies internal constructor(
  val routeDependencies: AppRouteDependencies,
  private val saveSharedBookmark: SaveSharedBookmarkUseCase,
) {
  suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult = saveSharedBookmark(url, title, sourceTitle)
}
