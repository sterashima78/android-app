package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiTextInference
import dev.terashima.yomitorirss.feature.podcast.PODCAST_OTHER_CATEGORY
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPodcastNewsCategorizerTest {
  @Test
  fun `tool callのカテゴリ配列をニュース順に返す`() {
    val categories = parsePodcastCategoryToolCall(
      AiStructuredToolCall(
        name = "submit_podcast_news_categories",
        arguments = mapOf("categories" to """["Technology","Economy"]"""),
      ),
      newsCount = 2,
    )

    assertEquals(listOf("Technology", "Economy"), categories)
  }

  @Test
  fun `カテゴリなしニュースはstructured toolでまとめて分類する`() = runSuspendForCategorizerTest {
    val textInference = FakeTextInference(promptBudgetChars = 4_096)
    val structured = FakeStructuredInference(
      ArrayDeque(
        listOf(
          AiStructuredToolCall(
            name = "submit_podcast_news_categories",
            arguments = mapOf("categories" to """["Technology","Diplomacy"]"""),
          ),
        ),
      ),
    )
    val categorizer = DefaultPodcastNewsCategorizer(
      localTextInference = textInference,
      cloudTextInference = textInference,
      localStructuredInference = structured,
      cloudStructuredInference = structured,
    )

    val result = categorizer.categorize(
      provider = PodcastGenerationProvider.CLOUD,
      news = listOf(
        listOf(feedEntry("a1", "AI chip launch")),
        listOf(feedEntry("a2", "Peace talks resume")),
      ),
      existingCategories = setOf("Technology"),
    )

    assertEquals(listOf("Technology", "Diplomacy"), result)
    assertEquals(0, textInference.generateCalls)
    assertEquals(1, structured.requests.size)
    val request = structured.requests.single()
    assertTrue(request.userMessage.contains("既存カテゴリ: Technology"))
    assertTrue(request.userMessage.contains("AI chip launch"))
    assertTrue(request.userMessage.contains("Peace talks resume"))
    assertFalse(request.userMessage.contains("本文"))
    assertFalse(request.userMessage.contains("https://"))
    assertEquals("submit_podcast_news_categories", request.tool.name)
  }

  @Test
  fun `カテゴリ分類結果が不正ならその他へfallbackする`() = runSuspendForCategorizerTest {
    val textInference = FakeTextInference(promptBudgetChars = 4_096)
    val structured = FakeStructuredInference(
      ArrayDeque(
        listOf(
          AiStructuredToolCall(
            name = "submit_podcast_news_categories",
            arguments = mapOf("categories" to """["Technology"]"""),
          ),
          AiStructuredToolCall(
            name = "submit_podcast_news_categories",
            arguments = mapOf("categories" to """[""]"""),
          ),
        ),
      ),
    )
    val categorizer = DefaultPodcastNewsCategorizer(
      localTextInference = textInference,
      cloudTextInference = textInference,
      localStructuredInference = structured,
      cloudStructuredInference = structured,
    )

    val result = categorizer.categorize(
      provider = PodcastGenerationProvider.CLOUD,
      news = listOf(listOf(feedEntry("a1")), listOf(feedEntry("a2"))),
      existingCategories = emptySet(),
    )

    assertEquals(listOf(PODCAST_OTHER_CATEGORY, PODCAST_OTHER_CATEGORY), result)
    assertEquals(2, structured.requests.size)
  }

  @Test
  fun `カテゴリpromptは上限内で全ニュース番号を残す`() {
    val news = (1..20).map { index ->
      listOf(feedEntry("a$index", "長いニュースタイトル".repeat(10)))
    }

    val prompt = buildPodcastCategorizationToolPrompt(news, setOf("Technology"), maxChars = 4_096)

    assertTrue(prompt.length <= 4_096)
    (1..20).forEach { index ->
      assertTrue(prompt.contains("\n$index. ") || prompt.startsWith("$index. "))
    }
  }

  private fun feedEntry(id: String, title: String = "記事 $id") = PodcastFeedEntry(
    articleId = id,
    feedId = "source-$id",
    title = title,
    sourceTitle = "情報源 $id",
    publishedAtEpochMillis = 100L,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文 $id",
  )
}

private class FakeTextInference(
  promptBudgetChars: Int,
) : BackgroundAiTextInference() {
  override val progress: Flow<AiTextInferenceProgress?> = flowOf(null)
  var generateCalls = 0

  private val model = AiTextInferenceModel(
    id = "test-model",
    name = "Test model",
    contextTokens = 8_192,
    maxInputChars = 2_500,
    promptBudgetChars = promptBudgetChars,
    cacheVariant = "test",
  )

  override fun selectedModel(): AiTextInferenceModel = model
  override fun countTokens(text: String): Int = text.length
  protected override suspend fun validateBackgroundExecution() = Unit

  protected override suspend fun generateInBackground(prompt: String): String {
    generateCalls += 1
    error("free-form generation must not be used")
  }
}

private class FakeStructuredInference(
  private val outputs: ArrayDeque<AiStructuredToolCall?>,
) : BackgroundAiStructuredTextInference() {
  data class Request(
    val systemInstruction: String,
    val userMessage: String,
    val tool: AiStructuredTool,
  )

  val requests = mutableListOf<Request>()

  protected override suspend fun validateBackgroundExecution() = Unit

  protected override suspend fun generateToolCallInBackground(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? {
    requests += Request(systemInstruction, userMessage, tool)
    return outputs.removeFirstOrNull()
  }
}

private fun <T> runSuspendForCategorizerTest(block: suspend () -> T): T {
  var value: Result<T>? = null
  block.startCoroutine(
    object : Continuation<T> {
      override val context = EmptyCoroutineContext
      override fun resumeWith(result: Result<T>) {
        value = result
      }
    },
  )
  return requireNotNull(value) { "suspend block did not complete synchronously" }.getOrThrow()
}
