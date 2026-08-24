package dev.terashima.yomitorirss.feature.reddit

import dev.terashima.yomitorirss.feature.article.Article

/**
 * Reddit-owned classification boundary used by consumers that must keep Reddit out of generic RSS
 * presentation and input handling without reimplementing Reddit URL rules.
 */
object RedditSourceBoundary {
  fun isNonRedditArticle(article: Article): Boolean = !article.isRedditArticle()

  fun isNonRedditFeed(feedUrl: String): Boolean = !isRedditFeedUrl(feedUrl)

  fun isRssSubscriptionInput(input: String): Boolean =
    redditCommunityFeedUrl(input) == null &&
      redditThreadId(input) == null &&
      !isRedditFeedUrl(input)
}
