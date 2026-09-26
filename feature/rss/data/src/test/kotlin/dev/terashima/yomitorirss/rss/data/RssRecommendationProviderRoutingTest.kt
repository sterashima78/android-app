package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.feature.rss.RssRecommendationDecision
import dev.terashima.yomitorirss.feature.rss.RssRecommendationExecutionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RssRecommendationProviderRoutingTest {
  @Test
  fun `クラウド選択時はcloud structured inferenceだけを使う`() = runBlocking {
    val local = RecordingStructuredInference(IllegalStateException("local inference must not be used"))
    val cloud = RecordingStructuredInference(
      AiStructuredToolCall(
        name = "submit_rss_recommendation_scores",
        arguments = mapOf(
          "statuses" to "[\"scored\"]",
          "scores" to "[\"8\"]",
        ),
      ),
    )
    val engine = DefaultRssRecommendationEngine(
      localStructuredInference = local,
      cloudStructuredInference = cloud,
    )

    val result = engine.score(
      provider = RssRecommendationExecutionProvider.CLOUD,
      condition = "広告記事を低くする",
      titles = listOf("通常記事"),
    )

    assertEquals(listOf(RssRecommendationDecision.Scored(8)), result)
    assertEquals(0, local.calls)
    assertEquals(1, cloud.calls)
  }
}

private class RecordingStructuredInference(
  private val result: Any,
) : AiStructuredTextInference {
  var calls: Int = 0

  override suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? {
    calls += 1
    if (result is Throwable) throw result
    return result as AiStructuredToolCall
  }
}
