package dev.terashima.yomitorirss.feature.reddit

import dev.terashima.yomitorirss.feature.article.Article

/**
 * Reddit-owned classification boundary used by consumers that must distinguish Reddit from generic
 * RSS presentation and input handling without reimplementing Reddit URL rules.
 */
object RedditSourceBoundary {
  fun isRedditArticle(article: Article): Boolean = article.isRedditArticle()

  fun isNonRedditArticle(article: Article): Boolean = !isRedditArticle(article)

  fun isRedditFeed(feedUrl: String): Boolean = isRedditFeedUrl(feedUrl)

  fun isNonRedditFeed(feedUrl: String): Boolean = !isRedditFeed(feedUrl)

  fun isRssSubscriptionInput(input: String): Boolean =
    redditCommunityFeedUrl(input) == null &&
      redditThreadId(input) == null &&
      !isRedditFeedUrl(input)
}
