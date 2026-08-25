package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import dev.terashima.yomitorirss.feature.summary.SummaryCloudGenerationResult
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInferenceException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal class ChatGptSummaryCloudInference(
  private val client: ChatGptOpenAiClient,
  private val modelPreferences: ChatGptModelPreferences,
) : SummaryCloudInference {
  override fun isAvailable(): Boolean =
    client.connectionStatus().connected && modelPreferences.selectedModelId() != null

  override fun selectedModelId(): String? = modelPreferences.selectedModelId()

  override suspend fun generate(prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel()
    val result = client.generate(modelId, prompt)
    SummaryCloudGenerationResult(result.modelId, result.text)
  }

  override suspend fun generateFromUrl(url: String, prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel()
    val result = client.generateWithWebSearch(modelId, prompt, url)
    SummaryCloudGenerationResult(result.modelId, result.text)
  }

  private fun requireSelectedModel(): String = modelPreferences.selectedModelId()
    ?: error("ChatGPT / Codex の利用モデルを選択してください")

  private suspend fun <T> mapProviderFailure(block: suspend () -> T): T = try {
    block()
  } catch (error: CancellationException) {
    throw error
  } catch (error: IOException) {
    throw classifyTransportFailure(error)
  } catch (error: IllegalStateException) {
    throw classifyProviderFailure(error)
  }
}

internal fun classifyTransportFailure(error: IOException): SummaryCloudInferenceException =
  SummaryCloudInferenceException(
    kind = SummaryCloudFailureKind.TRANSIENT,
    retryable = true,
    message = "ChatGPT / Codex との通信に失敗しました。自動的に再試行します",
  )

internal fun classifyProviderFailure(error: IllegalStateException): SummaryCloudInferenceException {
  val status = HTTP_STATUS_PATTERN.find(error.message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
  return when {
    status == 401 || status == 403 || isRefreshCredentialRejection(error, status) -> SummaryCloudInferenceException(
      kind = SummaryCloudFailureKind.AUTHENTICATION,
      retryable = false,
      message = "ChatGPT の認証が無効です。設定から再ログインしてください",
    )
    status == 408 || status == 429 || status != null && status in 500..599 -> SummaryCloudInferenceException(
      kind = if (status == 429) SummaryCloudFailureKind.RATE_LIMITED else SummaryCloudFailureKind.TRANSIENT,
      retryable = true,
      message = if (status == 429) {
        "ChatGPT / Codex の利用上限またはレート制限に達しました。自動的に再試行します"
      } else {
        "ChatGPT / Codex が一時的に利用できません。自動的に再試行します"
      },
    )
    status != null && status in 400..499 -> SummaryCloudInferenceException(
      kind = SummaryCloudFailureKind.REQUEST_REJECTED,
      retryable = false,
      message = "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP $status)",
    )
    else -> SummaryCloudInferenceException(
      kind = SummaryCloudFailureKind.UNKNOWN,
      retryable = false,
      message = sanitizeUnknownProviderMessage(error.message),
    )
  }
}

private fun isRefreshCredentialRejection(error: IllegalStateException, status: Int?): Boolean =
  status != null && status in 400..499 && error.message.orEmpty().contains("OAuth token refresh", ignoreCase = true)

private fun sanitizeUnknownProviderMessage(message: String?): String {
  val value = message.orEmpty()
  return when {
    value.contains("not connected", ignoreCase = true) -> "ChatGPT へ接続してください"
    value.contains("利用モデルを選択", ignoreCase = true) -> "ChatGPT / Codex の利用モデルを選択してください"
    value.contains("did not open the specified article URL", ignoreCase = true) ->
      "ChatGPT / Codex が指定した記事URLを開けませんでした"
    else -> "ChatGPT / Codex のクラウド推論に失敗しました"
  }
}

private val HTTP_STATUS_PATTERN = Regex("\\((\\d{3})\\)")
