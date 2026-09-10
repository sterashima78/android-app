package dev.terashima.yomitorirss.feature.rss

data class RssFeedContentEntry(
  val identityKey: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String,
  val publishedAtEpochMillis: Long?,
  val url: String,
  val content: String,
)

data class RssFeedContentSource(
  val id: String,
  val feedUrl: String,
)

/** Reads only content carried by RSS/Atom feeds. Linked article pages are never fetched. */
interface RssFeedContentReader {
  suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<RssFeedContentEntry>

  /** Reads ad-hoc feed sources without registering them in the RSS subscription lifecycle. */
  suspend fun latestEntriesFromSources(
    sources: List<RssFeedContentSource>,
    limit: Int,
  ): List<RssFeedContentEntry>
}
