package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssUnreadSortTest {
  private val policy = RssRecommendationPolicy(manualCondition = "不要な記事を低く評価する", revision = 2L)

  @Test
  fun `推薦スコア降順を日時より優先し同点は新しい順と古い順を切り替える`() {
    val highOld = article("high-old", "2026-08-11T09:00:00Z")
    val highNew = article("high-new", "2026-08-11T11:00:00Z")
    val lowNew = article("low-new", "2026-08-12T12:00:00Z")
    val unscored = article("unscored", "2026-08-13T13:00:00Z")
    val pending = article("pending", "2026-08-14T14:00:00Z")
    val state = RssUiState(
      recommendationPolicy = policy,
      recommendationAssessments = mapOf(
        highOld.id to scored(9),
        highNew.id to scored(9),
        lowNew.id to scored(3),
        unscored.id to RssRecommendationAssessment.Unscored(
          reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
          revision = 2L,
          assessedAt = 1L,
        ),
      ),
    )
    val articles = listOf(lowNew, pending, highOld, unscored, highNew)

    assertEquals(
      listOf("high-new", "high-old", "low-new", "pending", "unscored"),
      sortRssUnread(articles, state, newestFirst = true).map(Article::id),
    )
    assertEquals(
      listOf("high-old", "high-new", "low-new", "unscored", "pending"),
      sortRssUnread(articles, state, newestFirst = false).map(Article::id),
    )
  }

  @Test
  fun `旧revisionや無効な条件のスコアは並び順に使わない`() {
    val oldScored = article("stale", "2026-08-11T09:00:00Z")
    val newUnscored = article("new", "2026-08-12T10:00:00Z")
    val state = RssUiState(
      recommendationPolicy = policy,
      recommendationAssessments = mapOf(oldScored.id to scored(10, revision = 1L)),
    )

    assertNull(state.recommendationScoreFor(oldScored.id))
    assertEquals(
      listOf("new", "stale"),
      sortRssUnread(listOf(oldScored, newUnscored), state, newestFirst = true).map(Article::id),
    )
    assertEquals(
      listOf("stale", "new"),
      sortRssUnread(
        listOf(newUnscored, oldScored),
        state.copy(recommendationPolicy = RssRecommendationPolicy()),
        newestFirst = false,
      ).map(Article::id),
    )
  }

  private fun scored(score: Int, revision: Long = 2L) =
    RssRecommendationAssessment.Scored(score = score, revision = revision, assessedAt = 1L)

  private fun article(id: String, time: String) = Article(
    id = id,
    feedId = null,
    externalId = id,
    identityKey = id,
    url = "https://example.com/$id",
    title = id,
    publishedAt = time,
    fetchedAt = time,
    readAt = null,
    sourceTitle = id,
    sourceFeedUrl = "https://example.com/feed.xml",
  )
}
