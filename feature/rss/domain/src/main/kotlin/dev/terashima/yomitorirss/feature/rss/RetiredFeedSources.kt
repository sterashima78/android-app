package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import java.net.URI
import java.util.Locale

/**
 * Legacy feed records remain stored for backup continuity, but are no longer active subscriptions.
 * This guard is shared by the ingestion and presentation boundaries until a safe data migration exists.
 */
fun isRetiredRedditFeedUrl(url: String): Boolean {
  val input = url.trim()
  // Accept legacy stored URLs and user-entered URLs without a scheme.
  // The previous subscription UI also accepted community shorthand.
  if (RETIRED_COMMUNITY_SHORTHAND.matches(input)) return true
  val candidate = if ("://" in input) input else "https://$input"
  val host = runCatching { URI(candidate).host?.lowercase(Locale.ROOT) }.getOrNull()
  return host == "reddit.com" || host?.endsWith(".reddit.com") == true
}

private val RETIRED_COMMUNITY_SHORTHAND = Regex("r/[A-Za-z0-9_]{1,50}/?", RegexOption.IGNORE_CASE)

fun isActiveRssArticle(article: Article): Boolean = !isRetiredRedditFeedUrl(article.sourceFeedUrl)
