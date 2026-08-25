package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudFailureKind
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudInferenceException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class ChatGptKnowledgeTextInference(
  private val client: ChatGptOpenAiClient,
  private val modelPreferences: ChatGptModelPreferences,
) : AiTextInference {
  override val progress: Flow<AiTextInferenceProgress?> = emptyFlow()

  override fun selectedModel(): AiTextInferenceModel? = modelPreferences.selectedModelId()?.let { modelId ->
    AiTextInferenceModel(
      id = "chatgpt:$modelId",
      name = modelId,
      contextTokens = CLOUD_KNOWLEDGE_PROMPT_BUDGET_CHARS,
      maxInputChars = CLOUD_KNOWLEDGE_PROMPT_BUDGET_CHARS,
      promptBudgetChars = CLOUD_KNOWLEDGE_PROMPT_BUDGET_CHARS,
      cacheVariant = "chatgpt-knowledge-v1:$modelId",
    )
  }

  override fun countTokens(text: String): Int = text.toByteArray(Charsets.UTF_8).size

  override suspend fun generate(prompt: String): String = try {
    check(client.connectionStatus().connected) { "ChatGPT へ接続してください" }
    val modelId = modelPreferences.selectedModelId()
      ?: error("ChatGPT / Codex の利用モデルを選択してください")
    client.generate(modelId, prompt).text
  } catch (error: CancellationException) {
    throw error
  } catch (_: IOException) {
    throw classifyKnowledgeTransportFailure()
  } catch (error: IllegalStateException) {
    throw classifyKnowledgeProviderFailure(error)
  } catch (_: Throwable) {
    throw KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = "ChatGPT / Codex のWiki生成に失敗しました",
    )
  }
}

internal fun classifyKnowledgeTransportFailure(): KnowledgeCloudInferenceException =
  KnowledgeCloudInferenceException(
    kind = KnowledgeCloudFailureKind.TRANSIENT,
    retryable = true,
    message = "ChatGPT / Codex との通信に失敗しました。自動的に再試行します",
  )

internal fun classifyKnowledgeProviderFailure(error: IllegalStateException): KnowledgeCloudInferenceException {
  val status = KNOWLEDGE_HTTP_STATUS_PATTERN.find(error.message.orEmpty())
    ?.groupValues?.getOrNull(1)?.toIntOrNull()
  return when {
    status == 408 || status == 429 || status != null && status in 500..599 -> KnowledgeCloudInferenceException(
      kind = if (status == 429) KnowledgeCloudFailureKind.RATE_LIMITED else KnowledgeCloudFailureKind.TRANSIENT,
      retryable = true,
      message = if (status == 429) {
        "ChatGPT / Codex の利用上限またはレート制限に達しました。自動的に再試行します"
      } else {
        "ChatGPT / Codex が一時的に利用できません。自動的に再試行します"
      },
    )
    status == 401 || status == 403 || isKnowledgeRefreshCredentialRejection(error, status) ->
      KnowledgeCloudInferenceException(
        kind = KnowledgeCloudFailureKind.AUTHENTICATION,
        retryable = false,
        message = "ChatGPT の認証が無効です。設定から再ログインしてください",
      )
    status != null && status in 400..499 -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.REQUEST_REJECTED,
      retryable = false,
      message = "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP $status)",
    )
    else -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = sanitizeUnknownKnowledgeProviderMessage(error.message),
    )
  }
}

private fun isKnowledgeRefreshCredentialRejection(error: IllegalStateException, status: Int?): Boolean =
  status != null && status in 400..499 && error.message.orEmpty().contains("OAuth token refresh", ignoreCase = true)

private fun sanitizeUnknownKnowledgeProviderMessage(message: String?): String = when {
  message.orEmpty().contains("not connected", ignoreCase = true) ||
    message.orEmpty().contains("接続してください") -> "ChatGPT へ接続してください"
  message.orEmpty().contains("利用モデルを選択", ignoreCase = true) ->
    "ChatGPT / Codex の利用モデルを選択してください"
  else -> "ChatGPT / Codex のWiki生成に失敗しました"
}

private const val CLOUD_KNOWLEDGE_PROMPT_BUDGET_CHARS = 16_000
private val KNOWLEDGE_HTTP_STATUS_PATTERN = Regex("\\((\\d{3})\\)")
