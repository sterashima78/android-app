package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult

interface MainActivityDependenciesProvider {
  val mainActivityDependencies: MainActivityDependencies
}

class MainActivityDependencies internal constructor(
  val routeDependencies: AppRouteDependencies,
  private val bookmarkRepository: BookmarkRepository,
  private val onBookmarkChanged: () -> Unit,
) {
  suspend fun saveSharedArticle(
    url: String,
    title: String?,
    sourceTitle: String?,
  ): BookmarkSaveResult = bookmarkRepository.saveSharedArticle(url, title, sourceTitle)

  fun scheduleBackupAfterBookmarkChange() {
    onBookmarkChanged()
  }
}
