package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPodcastNewsClustererTest {
  @Test
  fun `tool callのgroup id配列をニュース群へ変換する`() {
    val groups = parsePodcastClusterToolCall(
      AiStructuredToolCall(
        name = "submit_podcast_news_clusters",
        arguments = mapOf("group_ids" to """["a","b","a"]"""),
      ),
      entryCount = 3,
    )

    assertEquals(listOf(listOf(0, 2), listOf(1)), groups)
  }

  @Test
  fun `group id数が記事数と異なるtool callは拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parsePodcastClusterToolCall(
        AiStructuredToolCall(
          name = "submit_podcast_news_clusters",
          arguments = mapOf("group_ids" to """["a","b"]"""),
        ),
        entryCount = 3,
      )
    }
  }

  @Test
  fun `分類は通常テキスト生成を使わずstructured toolを利用する`() = runSuspendForPodcastDataTest {
    val textInference = FakeTextInference(promptBudgetChars = 4_096)
    val structured = FakeStructuredInference(
      ArrayDeque(
        listOf(
          AiStructuredToolCall(
            name = "submit_podcast_news_clusters",
            arguments = mapOf("group_ids" to """["same","same","other"]"""),
          ),
        ),
      ),
    )
    val clusterer = DefaultPodcastNewsClusterer(
      localTextInference = textInference,
      cloudTextInference = textInference,
      localStructuredInference = structured,
      cloudStructuredInference = structured,
    )

    val result = clusterer.cluster(
      PodcastGenerationProvider.CLOUD,
      listOf(feedEntry("a1"), feedEntry("a2"), feedEntry("a3")),
    )

    assertEquals(PodcastClusteringStatus.SUCCESS, result.status)
    assertEquals(listOf(listOf(0, 1), listOf(2)), result.groups)
    assertEquals(0, textInference.generateCalls)
    assertEquals(1, structured.requests.size)
    assertEquals("submit_podcast_news_clusters", structured.requests.single().tool.name)
    assertFalse(structured.requests.single().tool.allowAdditionalArguments)
  }

  @Test
  fun `不正なtool argumentsは1回だけ再生成する`() = runSuspendForPodcastDataTest {
    val textInference = FakeTextInference(promptBudgetChars = 4_096)
    val structured = FakeStructuredInference(
      ArrayDeque(
        listOf(
          AiStructuredToolCall(
            name = "submit_podcast_news_clusters",
            arguments = mapOf("group_ids" to """["only-one"]"""),
          ),
          AiStructuredToolCall(
            name = "submit_podcast_news_clusters",
            arguments = mapOf("group_ids" to """["a","b"]"""),
          ),
        ),
      ),
    )
    val clusterer = DefaultPodcastNewsClusterer(
      localTextInference = textInference,
      cloudTextInference = textInference,
      localStructuredInference = structured,
      cloudStructuredInference = structured,
    )

    val result = clusterer.cluster(
      PodcastGenerationProvider.CLOUD,
      listOf(feedEntry("a1"), feedEntry("a2")),
    )

    assertEquals(PodcastClusteringStatus.SUCCESS, result.status)
    assertEquals(listOf(listOf(0), listOf(1)), result.groups)
    assertEquals(2, structured.requests.size)
    assertTrue(structured.requests.last().userMessage.contains("前回のtool call"))
  }

  @Test
  fun `29記事でもprompt上限内に全候補番号を残す`() {
    val entries = (1..29).map { index ->
      feedEntry("a$index").copy(title = "長いニュースタイトル".repeat(8) + index)
    }

    val prompt = buildPodcastClusteringToolPrompt(entries, maxChars = 4_096)

    assertTrue(prompt.length <= 4_096)
    (1..29).forEach { index ->
      assertTrue(prompt.contains("\n$index. ") || prompt.startsWith("$index. "))
    }
  }

  @Test
  fun `1記事だけならstructured推論を呼ばず分類を省略する`() = runSuspendForPodcastDataTest {
    val textInference = FakeTextInference(promptBudgetChars = 4_096)
    val structured = FakeStructuredInference(ArrayDeque())
    val clusterer = DefaultPodcastNewsClusterer(
      localTextInference = textInference,
      cloudTextInference = textInference,
      localStructuredInference = structured,
      cloudStructuredInference = structured,
    )

    val result = clusterer.cluster(PodcastGenerationProvider.CLOUD, listOf(feedEntry("a1")))

    assertEquals(PodcastClusteringStatus.SKIPPED, result.status)
    assertEquals(listOf(listOf(0)), result.groups)
    assertTrue(structured.requests.isEmpty())
  }

  private fun feedEntry(id: String) = PodcastFeedEntry(
    articleId = id,
    feedId = "source-$id",
    title = "記事 $id",
    sourceTitle = "情報源 $id",
    publishedAtEpochMillis = 100L,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文 $id",
  )
}

private class FakeTextInference(
  promptBudgetChars: Int,
) : AiTextInference {
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
  override suspend fun generate(prompt: String): String {
    generateCalls += 1
    error("free-form generation must not be used")
  }
}

private class FakeStructuredInference(
  private val outputs: ArrayDeque<AiStructuredToolCall?>,
) : AiStructuredTextInference {
  data class Request(
    val systemInstruction: String,
    val userMessage: String,
    val tool: AiStructuredTool,
  )

  val requests = mutableListOf<Request>()

  override suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? {
    requests += Request(systemInstruction, userMessage, tool)
    return outputs.removeFirstOrNull()
  }
}

private fun <T> runSuspendForPodcastDataTest(block: suspend () -> T): T {
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
