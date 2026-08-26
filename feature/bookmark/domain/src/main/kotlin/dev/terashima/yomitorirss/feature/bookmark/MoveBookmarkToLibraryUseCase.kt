package dev.terashima.yomitorirss.feature.bookmark

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.library.WebLibraryAdder

class MoveBookmarkToLibraryUseCase(
  private val webLibrary: WebLibraryAdder,
  private val bookmarkMutator: BookmarkMutator,
  private val onBookmarkChanged: () -> Unit = {},
) {
  suspend operator fun invoke(article: Article) {
    webLibrary.addWebBook(article.url, article.title)
    bookmarkMutator.unsaveArticle(article.id)
    onBookmarkChanged()
  }
}
