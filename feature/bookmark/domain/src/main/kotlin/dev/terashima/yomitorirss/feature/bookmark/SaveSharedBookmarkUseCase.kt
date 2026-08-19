package dev.terashima.yomitorirss.feature.bookmark

class SaveSharedBookmarkUseCase(
  private val saver: SharedBookmarkSaver,
  private val onBookmarkChanged: () -> Unit = {},
) {
  suspend operator fun invoke(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult {
    val result = saver.saveSharedArticle(url, title, sourceTitle)
    if (result == BookmarkSaveResult.ADDED) onBookmarkChanged()
    return result
  }
}
