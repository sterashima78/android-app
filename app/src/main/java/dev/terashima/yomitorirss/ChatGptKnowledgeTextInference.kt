package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptInferenceClient
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudFailureKind
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudInferenceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class ChatGptKnowledgeTextInference(
  private val client: ChatGptInferenceClient,
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
    val modelId = modelPreferences.selectedModelId()
      ?: error("ChatGPT / Codex の利用モデルを選択してください")
    client.generate(modelId, prompt).text
  } catch (error: CancellationException) {
    throw error
  } catch (error: ChatGptProviderException) {
    throw classifyKnowledgeProviderFailure(error)
  } catch (error: IllegalStateException) {
    throw KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = sanitizeUnknownKnowledgeAdapterMessage(error.message),
    )
  } catch (_: Throwable) {
    throw KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = "ChatGPT / Codex のWiki生成に失敗しました",
    )
  }
}

internal fun classifyKnowledgeProviderFailure(error: ChatGptProviderException): KnowledgeCloudInferenceException =
  when (error.kind) {
    ChatGptProviderFailureKind.TRANSIENT -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.TRANSIENT,
      retryable = error.retryable,
      message = "ChatGPT / Codex が一時的に利用できません。自動的に再試行します",
    )
    ChatGptProviderFailureKind.RATE_LIMITED -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.RATE_LIMITED,
      retryable = error.retryable,
      message = "ChatGPT / Codex の利用上限またはレート制限に達しました。自動的に再試行します",
    )
    ChatGptProviderFailureKind.AUTHENTICATION -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.AUTHENTICATION,
      retryable = false,
      message = "ChatGPT の認証が無効です。設定から再ログインしてください",
    )
    ChatGptProviderFailureKind.REQUEST_REJECTED -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.REQUEST_REJECTED,
      retryable = false,
      message = error.statusCode?.let { "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP $it)" }
        ?: "ChatGPT / Codex にリクエストを受け付けてもらえませんでした",
    )
    ChatGptProviderFailureKind.NOT_CONNECTED -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = "ChatGPT へ接続してください",
    )
    ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = "ChatGPT / Codex のWiki生成に失敗しました",
    )
    ChatGptProviderFailureKind.UNKNOWN -> KnowledgeCloudInferenceException(
      kind = KnowledgeCloudFailureKind.UNKNOWN,
      retryable = false,
      message = "ChatGPT / Codex のWiki生成に失敗しました",
    )
  }

private fun sanitizeUnknownKnowledgeAdapterMessage(message: String?): String = when {
  message.orEmpty().contains("利用モデルを選択", ignoreCase = true) ->
    "ChatGPT / Codex の利用モデルを選択してください"
  else -> "ChatGPT / Codex のWiki生成に失敗しました"
}

private const val CLOUD_KNOWLEDGE_PROMPT_BUDGET_CHARS = 16_000
