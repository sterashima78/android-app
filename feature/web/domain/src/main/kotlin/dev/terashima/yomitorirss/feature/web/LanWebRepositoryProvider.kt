package dev.terashima.yomitorirss.feature.web

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository

interface LanWebRepositoryProvider {
  val lanWebArticleRepository: ArticleRepository
  val lanWebBookmarkRepository: BookmarkRepository
  val lanWebFeedRepository: FeedRepository
}
