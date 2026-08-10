package dev.terashima.yomitorirss.feature.reddit

import java.net.URI
import java.util.Locale

fun redditCommunityFeedUrl(input: String): String? {
  val trimmed = input.trim()
  val shorthand = trimmed
    .takeIf { it.startsWith("r/", ignoreCase = true) }
    ?.substring(2)
    ?.trim('/')
    ?.takeIf { '/' !in it && it.isValidRedditName() }
  if (shorthand != null) return communityFeedUrl(shorthand)

  val uri = redditUri(trimmed) ?: return null
  if (!uri.isRedditHost()) return null
  val segments = uri.pathSegments()
  if (segments.size < 2 || !segments[0].equals("r", ignoreCase = true)) return null
  val community = segments[1].takeIf(String::isValidRedditName) ?: return null
  val suffix = segments.drop(2).map { it.lowercase(Locale.ROOT) }
  val isCommunityListing = when (suffix) {
    emptyList<String>() -> true
    listOf(".rss") -> true
    listOf("new") -> true
    listOf("new", ".rss") -> true
    else -> false
  }
  return communityFeedUrl(community).takeIf { isCommunityListing }
}

fun redditThreadId(input: String): String? {
  val uri = redditUri(input.trim()) ?: return null
  if (!uri.isRedditHost()) return null
  val segments = uri.pathSegments()
  val commentsIndex = segments.indexOfFirst { it.equals("comments", ignoreCase = true) }
  if (commentsIndex < 0 || commentsIndex + 1 >= segments.size) return null
  return segments[commentsIndex + 1]
    .takeIf { it.matches(REDDIT_POST_ID) }
    ?.lowercase(Locale.ROOT)
}

fun redditThreadFeedUrl(input: String): String? {
  val uri = redditUri(input.trim()) ?: return null
  if (!uri.isRedditHost()) return null
  val segments = uri.pathSegments()
  val commentsIndex = segments.indexOfFirst { it.equals("comments", ignoreCase = true) }
  if (commentsIndex < 0 || commentsIndex + 1 >= segments.size) return null
  val postId = segments[commentsIndex + 1]
    .takeIf { it.matches(REDDIT_POST_ID) }
    ?.lowercase(Locale.ROOT)
    ?: return null
  val community = if (
    commentsIndex >= 2 &&
    segments[commentsIndex - 2].equals("r", ignoreCase = true) &&
    segments[commentsIndex - 1].isValidRedditName()
  ) {
    segments[commentsIndex - 1].lowercase(Locale.ROOT)
  } else {
    null
  }
  return if (community == null) {
    "https://www.reddit.com/comments/$postId/.rss"
  } else {
    "https://www.reddit.com/r/$community/comments/$postId/.rss"
  }
}

fun isRedditFeedUrl(input: String): Boolean {
  val uri = redditUri(input.trim()) ?: return false
  if (!uri.isRedditHost()) return false
  val segments = uri.pathSegments()
  return segments.lastOrNull()?.equals(".rss", ignoreCase = true) == true ||
    segments.any { it.equals("comments", ignoreCase = true) } ||
    segments.take(1).any { it.equals("r", ignoreCase = true) }
}

fun redditSubscriptionKind(feedUrl: String): RedditSubscriptionKind? = when {
  redditThreadId(feedUrl) != null -> RedditSubscriptionKind.THREAD
  redditCommunityFeedUrl(feedUrl) != null -> RedditSubscriptionKind.COMMUNITY
  else -> null
}

private fun communityFeedUrl(community: String): String =
  "https://www.reddit.com/r/${community.lowercase(Locale.ROOT)}/new/.rss"

private fun redditUri(input: String): URI? {
  if (input.isBlank()) return null
  val candidate = when {
    input.startsWith("https://", ignoreCase = true) -> input
    input.startsWith("http://", ignoreCase = true) -> "https://${input.substring(7)}"
    input.startsWith("www.", ignoreCase = true) || input.startsWith("old.", ignoreCase = true) -> "https://$input"
    else -> return null
  }
  return runCatching { URI(candidate).normalize() }.getOrNull()
}

private fun URI.isRedditHost(): Boolean {
  val value = host?.lowercase(Locale.ROOT) ?: return false
  return value == "reddit.com" || value.endsWith(".reddit.com")
}

private fun URI.pathSegments(): List<String> =
  path.orEmpty().split('/').filter(String::isNotBlank)

private fun String.isValidRedditName(): Boolean = matches(REDDIT_NAME)

private val REDDIT_NAME = Regex("[A-Za-z0-9_]{1,50}")
private val REDDIT_POST_ID = Regex("[A-Za-z0-9]+")
