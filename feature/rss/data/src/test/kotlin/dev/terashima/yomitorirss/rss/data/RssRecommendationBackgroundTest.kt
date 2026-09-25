package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import org.junit.Assert.assertEquals
import org.junit.Test

class RssRecommendationBackgroundTest {
  @Test
  fun `未評価とstaleと推論失敗だけを再評価対象にする`() {
    val articles = listOf(
      article("missing"),
      article("stale"),
      article("failed"),
      article("scored"),
      article("insufficient"),
    )
    val assessments = mapOf(
      "stale" to RssRecommendationAssessment.Scored(
        score = 5,
        revision = 1L,
        assessedAt = 1L,
      ),
      "failed" to RssRecommendationAssessment.Unscored(
        reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
        revision = 2L,
        assessedAt = 1L,
      ),
      "scored" to RssRecommendationAssessment.Scored(
        score = 8,
        revision = 2L,
        assessedAt = 1L,
      ),
      "insufficient" to RssRecommendationAssessment.Unscored(
        reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
        revision = 2L,
        assessedAt = 1L,
      ),
    )

    val result = recommendationScoringCandidates(
      articles = articles,
      assessments = assessments,
      revision = 2L,
    )

    assertEquals(listOf("missing", "stale", "failed"), result.map(Article::id))
  }

  private fun article(id: String) = Article(
    id = id,
    feedId = "feed",
    externalId = id,
    identityKey = id,
    url = "https://example.invalid/$id",
    title = id,
    publishedAt = "2026-09-25T00:00:00Z",
    fetchedAt = "2026-09-25T00:00:00Z",
    readAt = null,
    sourceTitle = "test",
    sourceFeedUrl = "https://example.invalid/feed",
  )
}
