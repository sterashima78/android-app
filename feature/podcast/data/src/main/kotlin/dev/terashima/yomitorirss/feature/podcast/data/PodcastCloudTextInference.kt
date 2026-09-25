package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptInferenceClient
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

interface PodcastCloudTextInference {
  fun selectedModel(): AiTextInferenceModel?
  suspend fun generate(prompt: String): String
}

class DefaultPodcastCloudTextInference(
  private val client: ChatGptInferenceClient,
  private val modelPreferences: ChatGptModelPreferences,
  private val sleep: suspend (Long) -> Unit = { delay(it) },
  private val jitterMillis: (Long) -> Long = ::defaultPodcastRetryJitterMillis,
) : PodcastCloudTextInference {
  private val modelProjection = ChatGptTextInference(client, modelPreferences)

  override fun selectedModel(): AiTextInferenceModel? = modelProjection.selectedModel()

  override suspend fun generate(prompt: String): String {
    val modelId = modelPreferences.selectedModelId()
      ?: error("利用するクラウドAIモデルを選択してください")
    return try {
      retryPodcastCloudInference(
        sleep = sleep,
        jitterMillis = jitterMillis,
      ) {
        client.generate(modelId, prompt).text
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: ChatGptProviderException) {
      throw IllegalStateException(podcastCloudFailureMessage(error))
    }
  }
}

internal suspend fun <T> retryPodcastCloudInference(
  retryDelaysMillis: List<Long> = PODCAST_CLOUD_RETRY_DELAYS_MILLIS,
  sleep: suspend (Long) -> Unit = { delay(it) },
  jitterMillis: (Long) -> Long = ::defaultPodcastRetryJitterMillis,
  block: suspend () -> T,
): T {
  var retryIndex = 0
  while (true) {
    try {
      return block()
    } catch (error: CancellationException) {
      throw error
    } catch (error: ChatGptProviderException) {
      if (!error.retryable || retryIndex >= retryDelaysMillis.size) throw error
      val baseDelay = retryDelaysMillis[retryIndex++]
      sleep((baseDelay + jitterMillis(baseDelay)).coerceAtLeast(0L))
    }
  }
}

internal fun defaultPodcastRetryJitterMillis(baseDelayMillis: Long): Long {
  if (baseDelayMillis <= 0L) return 0L
  val maxJitter = (baseDelayMillis / 5L).coerceAtLeast(1L)
  return Random.nextLong(0L, maxJitter + 1L)
}

internal fun podcastCloudFailureMessage(error: ChatGptProviderException): String = when (error.kind) {
  ChatGptProviderFailureKind.TRANSIENT -> "クラウドAIが一時的に利用できません。自動再試行に失敗しました"
  ChatGptProviderFailureKind.RATE_LIMITED -> "クラウドAIの利用上限またはレート制限が続いています"
  ChatGptProviderFailureKind.AUTHENTICATION -> "クラウドAIの認証が無効です。設定から再接続してください"
  ChatGptProviderFailureKind.REQUEST_REJECTED -> "クラウドAIにリクエストを受け付けてもらえませんでした"
  ChatGptProviderFailureKind.NOT_CONNECTED -> "クラウドAIへ接続してください"
  ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED,
  ChatGptProviderFailureKind.UNKNOWN -> "クラウドAIのテキスト生成に失敗しました"
}

internal val PODCAST_CLOUD_RETRY_DELAYS_MILLIS = listOf(2_000L, 5_000L, 15_000L)
