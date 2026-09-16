package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.podcast.PodcastCandidateFilter
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry

class SqlitePodcastCandidateFilter(
  private val database: DatabaseConnection,
) : PodcastCandidateFilter {
  override suspend fun unconsumedEntries(
    programId: String,
    candidates: List<PodcastFeedEntry>,
  ): List<PodcastFeedEntry> {
    if (candidates.isEmpty()) return emptyList()
    val consumedIds = database.readable.rawQuery(
      "SELECT article_id FROM podcast_consumed_articles WHERE program_id=?",
      arrayOf(programId),
    ).use { cursor ->
      buildSet {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
    return candidates.filterNot { it.articleId in consumedIds }
  }
}
