package dev.terashima.yomitorirss.feature.rss

data class RssFeedContentEntry(
  val identityKey: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String,
  val publishedAtEpochMillis: Long?,
  val content: String,
)

/** Reads only content carried by the configured RSS/Atom feeds. Linked article pages are never fetched. */
interface RssFeedContentReader {
  suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<RssFeedContentEntry>
}
