package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationDecision
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedback
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRecommendationToolCallTest {
  @Test
  fun `スコアリングpromptは条件とタイトルだけを含む`() {
    val prompt = buildScoringPrompt(
      condition = "広告記事を低くする",
      titles = listOf("記事A", "記事B"),
    )

    assertTrue(prompt.contains("広告記事を低くする"))
    assertTrue(prompt.contains("1. 記事A"))
    assertTrue(prompt.contains("2. 記事B"))
    assertTrue(!prompt.contains("https://"))
  }

  @Test
  fun `学習promptはscore10と推論失敗を区別する`() {
    val prompt = buildLearningPrompt(
      manualCondition = "広告を低くする",
      learnedCondition = "",
      feedback = listOf(
        RssRecommendationFeedback(
          id = "f1",
          articleId = "a1",
          title = "通常と判定された記事",
          previousAssessment = RssRecommendationAssessment.Scored(
            score = 10,
            revision = 1L,
            assessedAt = 10L,
          ),
          createdAt = 20L,
        ),
        RssRecommendationFeedback(
          id = "f2",
          articleId = "a2",
          title = "判定に失敗した記事",
          previousAssessment = RssRecommendationAssessment.Unscored(
            reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
            revision = 1L,
            assessedAt = 11L,
          ),
          createdAt = 21L,
        ),
      ),
    )

    assertTrue(prompt.contains("score=10"))
    assertTrue(prompt.contains("unscored:INFERENCE_FAILED"))
  }

  @Test
  fun `評価可能な記事は1から10のスコアとして受け取る`() {
    val result = parseScoringToolCall(
      AiStructuredToolCall(
        name = "submit_rss_recommendation_scores",
        arguments = mapOf(
          "statuses" to """["scored","scored"]""",
          "scores" to """["3","10"]""",
        ),
      ),
      entryCount = 2,
    )

    assertEquals(
      listOf(
        RssRecommendationDecision.Scored(3),
        RssRecommendationDecision.Scored(10),
      ),
      result,
    )
  }

  @Test
  fun `タイトルだけで判断できない記事は数値化しない`() {
    val result = parseScoringToolCall(
      AiStructuredToolCall(
        name = "submit_rss_recommendation_scores",
        arguments = mapOf(
          "statuses" to """["insufficient_information"]""",
          "scores" to """["none"]""",
        ),
      ),
      entryCount = 1,
    )

    assertEquals(listOf(RssRecommendationDecision.InsufficientInformation), result)
  }

  @Test
  fun `候補数と結果数が一致しないtool callを拒否する`() {
    val error = runCatching {
      parseScoringToolCall(
        AiStructuredToolCall(
          name = "submit_rss_recommendation_scores",
          arguments = mapOf(
            "statuses" to """["scored"]""",
            "scores" to """["10"]""",
          ),
        ),
        entryCount = 2,
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `範囲外のスコアを拒否する`() {
    val error = runCatching {
      parseScoringToolCall(
        AiStructuredToolCall(
          name = "submit_rss_recommendation_scores",
          arguments = mapOf(
            "statuses" to """["scored"]""",
            "scores" to """["11"]""",
          ),
        ),
        entryCount = 1,
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `指定外のtool callを拒否する`() {
    val error = runCatching {
      parseScoringToolCall(
        AiStructuredToolCall(
          name = "other_tool",
          arguments = mapOf(
            "statuses" to """["scored"]""",
            "scores" to """["10"]""",
          ),
        ),
        entryCount = 1,
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }
}
