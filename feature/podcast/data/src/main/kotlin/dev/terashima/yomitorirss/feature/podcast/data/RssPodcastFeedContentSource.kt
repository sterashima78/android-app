package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedContentSource
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.rss.RssFeedContentReader

class RssPodcastFeedContentSource(
  private val reader: RssFeedContentReader,
  private val articleRepository: ArticleRepository,
) : PodcastFeedContentSource {
  override suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<PodcastFeedEntry> {
    if (feedIds.isEmpty() || limit <= 0) return emptyList()

    val unreadBySourceIdentity = articleRepository.listUnreadArticles()
      .asSequence()
      .filter { article -> article.feedId != null && article.feedId in feedIds }
      .associateBy { article -> requireNotNull(article.feedId) to article.identityKey }
    if (unreadBySourceIdentity.isEmpty()) return emptyList()

    return reader.latestEntries(feedIds, Int.MAX_VALUE)
      .mapNotNull { entry ->
        val article = unreadBySourceIdentity[entry.feedId to entry.identityKey] ?: return@mapNotNull null
        PodcastFeedEntry(
          articleId = article.id,
          feedId = entry.feedId,
          title = article.title,
          sourceTitle = article.sourceTitle,
          publishedAtEpochMillis = entry.publishedAtEpochMillis,
          feedContent = entry.content,
        )
      }
      .take(limit)
  }
}
