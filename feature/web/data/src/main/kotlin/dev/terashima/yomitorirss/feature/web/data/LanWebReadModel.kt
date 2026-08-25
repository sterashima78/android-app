package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedRepository

internal class LanWebReadModel(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val feedRepository: FeedRepository,
) {
  suspend fun loadHome(requestedView: String?): LanWebHomePage {
    val view = requestedView?.takeIf { it in LanWebViews.all } ?: LanWebViews.UNREAD
    return when (view) {
      LanWebViews.REDDIT -> LanWebHomePage(
        view = view,
        title = "Reddit",
        content = LanWebContent.Articles(
          articles = articleRepository.listUnreadArticles().filter(RedditSourceBoundary::isRedditArticle),
          emptyText = "Redditの未読はありません。",
        ),
      )
      LanWebViews.SAVED -> LanWebHomePage(
        view = view,
        title = "ブックマーク",
        content = LanWebContent.Bookmarks(
          articles = bookmarkRepository.listSavedArticles(null, null),
          emptyText = "ブックマークはありません。",
        ),
      )
      LanWebViews.READ_LATER -> LanWebHomePage(
        view = view,
        title = "あとで読む",
        content = LanWebContent.Bookmarks(
          articles = bookmarkRepository.listReadLaterArticles(),
          emptyText = "あとで読む記事はありません。",
        ),
      )
      LanWebViews.FEEDS -> LanWebHomePage(
        view = view,
        title = "RSSフィード",
        content = LanWebContent.Feeds(
          feeds = feedRepository.listFeeds().filter { RedditSourceBoundary.isNonRedditFeed(it.feedUrl) },
        ),
      )
      else -> LanWebHomePage(
        view = LanWebViews.UNREAD,
        title = "RSS未読",
        content = LanWebContent.Articles(
          articles = articleRepository.listUnreadArticles().filter(RedditSourceBoundary::isNonRedditArticle),
          emptyText = "RSSの未読記事はありません。",
        ),
      )
    }
  }
}

internal data class LanWebHomePage(
  val view: String,
  val title: String,
  val content: LanWebContent,
)

internal sealed interface LanWebContent {
  data class Articles(
    val articles: List<Article>,
    val emptyText: String,
  ) : LanWebContent

  data class Bookmarks(
    val articles: List<BookmarkedArticle>,
    val emptyText: String,
  ) : LanWebContent

  data class Feeds(
    val feeds: List<Feed>,
  ) : LanWebContent
}

internal object LanWebViews {
  const val UNREAD = "unread"
  const val REDDIT = "reddit"
  const val SAVED = "saved"
  const val READ_LATER = "read-later"
  const val FEEDS = "feeds"

  val all: Set<String> = setOf(UNREAD, REDDIT, SAVED, READ_LATER, FEEDS)
}
