package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.podcast.PodcastFeedContentSource
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastSource
import dev.terashima.yomitorirss.feature.rss.RssFeedContentReader
import dev.terashima.yomitorirss.feature.rss.RssFeedContentSource
import java.text.Normalizer
import java.util.Locale

class RssPodcastFeedContentSource(
  private val reader: RssFeedContentReader,
) : PodcastFeedContentSource {
  override suspend fun latestEntries(sources: List<PodcastSource>, limit: Int): List<PodcastFeedEntry> {
    if (sources.isEmpty() || limit <= 0) return emptyList()
    return reader.latestEntriesFromSources(
      sources = sources.map { RssFeedContentSource(id = it.id, feedUrl = it.feedUrl) },
      limit = limit,
    ).map { entry ->
      PodcastFeedEntry(
        articleId = "${entry.feedId}:${entry.identityKey}",
        feedId = entry.feedId,
        title = entry.title,
        sourceTitle = entry.sourceTitle,
        publishedAtEpochMillis = entry.publishedAtEpochMillis,
        articleUrl = entry.url,
        feedContent = entry.content,
      )
    }.distinctBy { entry -> normalizeTitleForDeduplication(entry.title) }
  }
}

private fun normalizeTitleForDeduplication(title: String): String =
  Normalizer.normalize(title, Normalizer.Form.NFKC)
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)
