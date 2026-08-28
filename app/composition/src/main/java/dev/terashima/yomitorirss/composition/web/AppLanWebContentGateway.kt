package dev.terashima.yomitorirss.composition.web

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.web.LanWebArticleItem
import dev.terashima.yomitorirss.feature.web.LanWebContentGateway
import dev.terashima.yomitorirss.feature.web.LanWebFeedItem
import dev.terashima.yomitorirss.feature.web.LanWebSourceKind

internal class AppLanWebContentGateway(
  private val articleRepository: ArticleRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val feedRepository: FeedRepository,
) : LanWebContentGateway {
  override suspend fun listUnreadArticles(): List<LanWebArticleItem> =
    articleRepository.listUnreadArticles().map(::articleItem)

  override suspend fun listSavedArticles(): List<LanWebArticleItem> =
    bookmarkRepository.listSavedArticles(null, null).map { bookmarked ->
      articleItem(bookmarked.article, bookmarked.tags.map { it.name })
    }

  override suspend fun listReadLaterArticles(): List<LanWebArticleItem> =
    bookmarkRepository.listReadLaterArticles().map { bookmarked ->
      articleItem(bookmarked.article, bookmarked.tags.map { it.name })
    }

  override suspend fun listFeeds(): List<LanWebFeedItem> =
    feedRepository.listFeeds().map(::feedItem)

  private fun articleItem(
    article: Article,
    tagNames: List<String> = emptyList(),
  ): LanWebArticleItem = LanWebArticleItem(
    title = article.title,
    url = article.url,
    sourceTitle = article.sourceTitle,
    publishedAt = article.publishedAt,
    sourceKind = if (RedditSourceBoundary.isRedditArticle(article)) {
      LanWebSourceKind.REDDIT
    } else {
      LanWebSourceKind.RSS
    },
    tagNames = tagNames,
  )

  private fun feedItem(feed: Feed): LanWebFeedItem = LanWebFeedItem(
    title = feed.title,
    feedUrl = feed.feedUrl,
    siteUrl = feed.siteUrl,
    sourceKind = if (RedditSourceBoundary.isNonRedditFeed(feed.feedUrl)) {
      LanWebSourceKind.RSS
    } else {
      LanWebSourceKind.REDDIT
    },
  )
}
