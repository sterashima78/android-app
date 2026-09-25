package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.feature.rss.RssRecommendationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRecommendationToolCallTest {
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
