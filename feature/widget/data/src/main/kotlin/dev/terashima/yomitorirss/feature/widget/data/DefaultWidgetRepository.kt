package dev.terashima.yomitorirss.feature.widget.data

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.widget.WidgetArticle
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import kotlinx.coroutines.runBlocking

class DefaultWidgetRepository(
  private val articleRepository: ArticleRepository,
  private val feedRepository: FeedRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val sourceSelector: (String) -> Boolean = { true },
) : WidgetRepository {
  override fun listUnreadArticles(): List<WidgetArticle> = runBlocking {
    articleRepository.listUnreadArticles()
  }.asSequence()
    .filter { article -> sourceSelector(article.sourceFeedUrl) }
    .map { article ->
      WidgetArticle(
        id = article.id,
        url = article.url,
        title = article.title,
        sourceTitle = article.sourceTitle,
        publishedAt = article.publishedAt,
      )
    }
    .toList()

  override suspend fun markRead(articleId: String) {
    articleRepository.markArticleRead(articleId)
  }

  override suspend fun markReadLater(articleId: String) {
    bookmarkRepository.markReadLater(articleId)
  }

  override suspend fun refreshFeeds() {
    feedRepository.listFeeds()
      .filter { sourceSelector(it.feedUrl) }
      .forEach { feed ->
        runCatching { feedRepository.refreshFeed(feed) }
      }
  }
}
