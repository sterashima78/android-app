package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.rss.RssFeedContentEntry
import dev.terashima.yomitorirss.feature.rss.RssFeedContentReader
import dev.terashima.yomitorirss.feature.rss.RssFeedContentSource
import dev.terashima.yomitorirss.feature.rss.data.network.FeedClient
import java.time.Instant

class DefaultRssFeedContentReader internal constructor(
  private val database: DatabaseConnection,
  private val feedClient: FeedClient,
) : RssFeedContentReader {
  constructor(database: DatabaseConnection, httpClient: HttpClient) : this(database, FeedClient(httpClient))

  override suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<RssFeedContentEntry> {
    if (feedIds.isEmpty() || limit <= 0) return emptyList()
    return latestEntriesFromSources(
      sources = readFeeds(feedIds).map { RssFeedContentSource(it.id, it.feedUrl) },
      limit = limit,
    )
  }

  override suspend fun latestEntriesFromSources(
    sources: List<RssFeedContentSource>,
    limit: Int,
  ): List<RssFeedContentEntry> {
    if (sources.isEmpty() || limit <= 0) return emptyList()
    return sources.flatMap { source ->
      val parsed = feedClient.fetchFeed(source.feedUrl).feed ?: return@flatMap emptyList()
      parsed.articles.mapNotNull { article ->
        article.feedContent.takeIf(String::isNotBlank)?.let { content ->
          RssFeedContentEntry(
            identityKey = article.identityKey,
            feedId = source.id,
            title = article.title,
            sourceTitle = parsed.title,
            publishedAtEpochMillis = runCatching { Instant.parse(article.publishedAt).toEpochMilli() }.getOrNull(),
            content = content,
          )
        }
      }
    }
      .sortedWith(compareByDescending<RssFeedContentEntry> { it.publishedAtEpochMillis ?: Long.MIN_VALUE })
      .take(limit)
  }

  private fun readFeeds(feedIds: Set<String>): List<FeedReference> {
    val ids = feedIds.toList()
    val placeholders = ids.joinToString(",") { "?" }
    return database.readable.rawQuery(
      "SELECT id,feed_url FROM feeds WHERE id IN($placeholders)",
      ids.toTypedArray(),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(FeedReference(cursor.getString(0), cursor.getString(1)))
      }
    }
  }

  private data class FeedReference(
    val id: String,
    val feedUrl: String,
  )
}
