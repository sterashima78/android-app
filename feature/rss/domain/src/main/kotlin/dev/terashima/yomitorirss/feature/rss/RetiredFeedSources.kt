package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import java.net.URI
import java.util.Locale

/**
 * Legacy feed records remain stored for backup continuity, but are no longer active subscriptions.
 * This guard is shared by the ingestion and presentation boundaries until a safe data migration exists.
 */
fun isRetiredRedditFeedUrl(url: String): Boolean {
  val host = runCatching { URI(url.trim()).host?.lowercase(Locale.ROOT) }.getOrNull()
  return host == "reddit.com" || host?.endsWith(".reddit.com") == true
}

fun isActiveRssArticle(article: Article): Boolean = !isRetiredRedditFeedUrl(article.sourceFeedUrl)
