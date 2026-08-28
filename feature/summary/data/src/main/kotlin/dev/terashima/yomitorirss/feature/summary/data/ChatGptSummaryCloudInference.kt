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

class ChatGptSummaryCloudInference(private val client: ChatGptInferenceClient, private val modelPreferences: ChatGptModelPreferences) : SummaryCloudInference {
  override fun isAvailable(): Boolean = client.connectionStatus().connected && modelPreferences.selectedModelId() != null
  override fun selectedModelId(): String? = modelPreferences.selectedModelId()
  override suspend fun generate(prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel(); val result = client.generate(modelId, prompt); SummaryCloudGenerationResult(result.modelId, result.text)
  }
  override suspend fun generateFromUrl(url: String, prompt: String): SummaryCloudGenerationResult = mapProviderFailure {
    val modelId = requireSelectedModel(); val result = client.generateWithWebSearch(modelId, prompt, url); SummaryCloudGenerationResult(result.modelId, result.text)
  }
  private fun requireSelectedModel(): String = modelPreferences.selectedModelId() ?: error("ChatGPT / Codex の利用モデルを選択してください")
  private suspend fun <T> mapProviderFailure(block: suspend () -> T): T = try { block()
  } catch (error: CancellationException) { throw error
  } catch (error: ChatGptProviderException) { throw classifySummaryProviderFailure(error)
  } catch (error: IllegalStateException) { throw SummaryCloudInferenceException(SummaryCloudFailureKind.UNKNOWN, false, sanitizeUnknownAdapterMessage(error.message)) }
}

internal fun classifySummaryProviderFailure(error: ChatGptProviderException): SummaryCloudInferenceException = when (error.kind) {
  ChatGptProviderFailureKind.TRANSIENT -> SummaryCloudInferenceException(SummaryCloudFailureKind.TRANSIENT, error.retryable, "ChatGPT / Codex が一時的に利用できません。自動的に再試行します")
  ChatGptProviderFailureKind.RATE_LIMITED -> SummaryCloudInferenceException(SummaryCloudFailureKind.RATE_LIMITED, error.retryable, "ChatGPT / Codex の利用上限またはレート制限に達しました。自動的に再試行します")
  ChatGptProviderFailureKind.AUTHENTICATION -> SummaryCloudInferenceException(SummaryCloudFailureKind.AUTHENTICATION, false, "ChatGPT の認証が無効です。設定から再ログインしてください")
  ChatGptProviderFailureKind.REQUEST_REJECTED -> SummaryCloudInferenceException(SummaryCloudFailureKind.REQUEST_REJECTED, false, error.statusCode?.let { "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP $it)" } ?: "ChatGPT / Codex にリクエストを受け付けてもらえませんでした")
  ChatGptProviderFailureKind.NOT_CONNECTED -> SummaryCloudInferenceException(SummaryCloudFailureKind.UNKNOWN, false, "ChatGPT へ接続してください")
  ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED -> SummaryCloudInferenceException(SummaryCloudFailureKind.UNKNOWN, false, "ChatGPT / Codex が指定した記事URLを開けませんでした")
  ChatGptProviderFailureKind.UNKNOWN -> SummaryCloudInferenceException(SummaryCloudFailureKind.UNKNOWN, false, "ChatGPT / Codex のクラウド推論に失敗しました")
}

private fun sanitizeUnknownAdapterMessage(message: String?): String = if (message.orEmpty().contains("利用モデルを選択", true)) "ChatGPT / Codex の利用モデルを選択してください" else "ChatGPT / Codex のクラウド推論に失敗しました"
