package dev.terashima.yomitorirss.feature.widget.data

import android.util.Log

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.markReadLaterWithPriorityFeedback
import dev.terashima.yomitorirss.feature.widget.WidgetArticle
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import kotlinx.coroutines.runBlocking

class DefaultWidgetRepository(
  private val articleRepository: ArticleRepository,
  private val feedRepository: FeedRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val sourceSelector: (String) -> Boolean = { true },
  private val onReadLaterSaved: ((Article) -> Unit)? = null,
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
    val notifier = onReadLaterSaved
    markReadLaterWithPriorityFeedback(
      saveReadLater = { bookmarkRepository.markReadLater(articleId) },
      recordPriority = notifier?.let { callback ->
        suspend {
          articleRepository.findArticle(articleId)
            ?.takeIf { sourceSelector(it.sourceFeedUrl) }
            ?.let(callback)
          Unit
        }
      },
      // The application-scoped callback registers the feedback and schedules learning together.
      scheduleLearning = {},
      onPriorityError = { error ->
        Log.w("UnreadWidget", "Could not register RSS priority reference after saving", error)
      },
    )
  }

  override suspend fun refreshFeeds() {
    feedRepository.listFeeds()
      .filter { sourceSelector(it.feedUrl) }
      .forEach { feed ->
        runCatching { feedRepository.refreshFeed(feed) }
      }
  }
}
