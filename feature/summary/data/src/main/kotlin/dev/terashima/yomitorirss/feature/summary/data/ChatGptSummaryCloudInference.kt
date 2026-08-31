package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptInferenceClient
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import dev.terashima.yomitorirss.feature.summary.SummaryCloudGenerationResult
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInferenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class ChatGptSummaryCloudInference(
  private val client: ChatGptInferenceClient,
  private val modelPreferences: ChatGptModelPreferences,
) : SummaryCloudInference {
  override fun isAvailable(): Boolean = client.connectionStatus().connected && modelPreferences.selectedModelId() != null

  override fun selectedModelId(): String? = modelPreferences.selectedModelId()

  override suspend fun generate(prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel()
    val result = client.generate(modelId, prompt)
    SummaryCloudGenerationResult(result.modelId, result.text)
  }

  override suspend fun generateFromUrl(url: String, prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel()
    val guardedPrompt = webFetchGuardedPrompt(prompt)
    val result = retryWebTargetOpen {
      val generated = client.generateWithWebSearch(modelId, guardedPrompt, url)
      if (isLikelyWebFetchFailureText(generated.text)) {
        throw ChatGptProviderException(
          kind = ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED,
          retryable = true,
          statusCode = null,
          message = "ChatGPT / Codex web target content was unavailable",
        )
      }
      generated
    }
    SummaryCloudGenerationResult(result.modelId, result.text)
  }

  private fun requireSelectedModel(): String = modelPreferences.selectedModelId()
    ?: error("ChatGPT / Codex の利用モデルを選択してください")

  private suspend fun <T> mapProviderFailure(block: suspend () -> T): T = try {
    block()
  } catch (error: CancellationException) {
    throw error
  } catch (error: ChatGptProviderException) {
    throw classifySummaryProviderFailure(error)
  } catch (error: IllegalStateException) {
    throw SummaryCloudInferenceException(
      SummaryCloudFailureKind.UNKNOWN,
      false,
      sanitizeUnknownAdapterMessage(error.message),
    )
  }
}

internal suspend fun <T> retryWebTargetOpen(
  maxAttempts: Int = CLOUD_WEB_FETCH_MAX_ATTEMPTS,
  baseDelayMillis: Long = CLOUD_WEB_FETCH_RETRY_BASE_DELAY_MILLIS,
  block: suspend () -> T,
): T {
  require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
  require(baseDelayMillis >= 0L) { "baseDelayMillis must not be negative" }
  for (attempt in 1..maxAttempts) {
    try {
      return block()
    } catch (error: CancellationException) {
      throw error
    } catch (error: ChatGptProviderException) {
      if (error.kind != ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED || attempt == maxAttempts) {
        throw error
      }
      if (baseDelayMillis > 0L) delay(baseDelayMillis * attempt)
    }
  }
  error("unreachable")
}

internal fun isLikelyWebFetchFailureText(value: String): Boolean {
  val normalized = value.trim().lowercase()
  if (normalized == CLOUD_WEB_FETCH_FAILURE_SENTINEL.lowercase()) return true
  if (normalized.length > CLOUD_WEB_FETCH_FAILURE_TEXT_MAX_CHARS) return false
  val firstParagraph = normalized.substringBefore("\n\n").trim()
  return WEB_FETCH_FAILURE_PHRASES.any(firstParagraph::contains)
}

private fun webFetchGuardedPrompt(prompt: String): String = """
  $prompt

  重要: 指定URLの記事本文を実際に取得できなかった場合は、検索結果の断片・別ページ・事前知識から推測せず、
  $CLOUD_WEB_FETCH_FAILURE_SENTINEL
  だけを返してください。
""".trimIndent()

internal fun classifySummaryProviderFailure(error: ChatGptProviderException): SummaryCloudInferenceException = when (error.kind) {
  ChatGptProviderFailureKind.TRANSIENT -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.TRANSIENT,
    error.retryable,
    "ChatGPT / Codex が一時的に利用できません。自動的に再試行します",
  )
  ChatGptProviderFailureKind.RATE_LIMITED -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.RATE_LIMITED,
    error.retryable,
    "ChatGPT / Codex の利用上限またはレート制限に達しました。自動的に再試行します",
  )
  ChatGptProviderFailureKind.AUTHENTICATION -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.AUTHENTICATION,
    false,
    "ChatGPT の認証が無効です。設定から再ログインしてください",
  )
  ChatGptProviderFailureKind.REQUEST_REJECTED -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.REQUEST_REJECTED,
    false,
    error.statusCode?.let { "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP $it)" }
      ?: "ChatGPT / Codex にリクエストを受け付けてもらえませんでした",
  )
  ChatGptProviderFailureKind.NOT_CONNECTED -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.UNKNOWN,
    false,
    "ChatGPT へ接続してください",
  )
  ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.UNKNOWN,
    false,
    "ChatGPT / Codex が指定した記事URLを複数回試しましたが取得できませんでした",
  )
  ChatGptProviderFailureKind.UNKNOWN -> SummaryCloudInferenceException(
    SummaryCloudFailureKind.UNKNOWN,
    false,
    "ChatGPT / Codex のクラウド推論に失敗しました",
  )
}

private fun sanitizeUnknownAdapterMessage(message: String?): String =
  if (message.orEmpty().contains("利用モデルを選択", true)) {
    "ChatGPT / Codex の利用モデルを選択してください"
  } else {
    "ChatGPT / Codex のクラウド推論に失敗しました"
  }

private const val CLOUD_WEB_FETCH_MAX_ATTEMPTS = 3
private const val CLOUD_WEB_FETCH_RETRY_BASE_DELAY_MILLIS = 500L
private const val CLOUD_WEB_FETCH_FAILURE_TEXT_MAX_CHARS = 400
private const val CLOUD_WEB_FETCH_FAILURE_SENTINEL = "__YOMITORI_WEB_FETCH_FAILED__"

private val WEB_FETCH_FAILURE_PHRASES = listOf(
  "ページを取得できません",
  "ページを開けません",
  "記事を取得できません",
  "記事を開けません",
  "記事urlを開けません",
  "unable to access the page",
  "unable to access the article",
  "could not access the page",
  "couldn't access the page",
  "unable to open the page",
  "could not open the page",
  "couldn't open the page",
  "failed to fetch the page",
  "unable to retrieve the page",
  "could not retrieve the page",
)
