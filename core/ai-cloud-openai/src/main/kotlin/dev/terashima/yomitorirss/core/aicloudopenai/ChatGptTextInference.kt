package dev.terashima.yomitorirss.core.aicloudopenai

import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Provider-neutral one-shot text inference backed by the ChatGPT / Codex adapter. */
class ChatGptTextInference(
  private val client: ChatGptInferenceClient,
  private val modelPreferences: ChatGptModelPreferences,
) : AiTextInference {
  override val progress: Flow<AiTextInferenceProgress?> = emptyFlow()

  override fun selectedModel(): AiTextInferenceModel? = modelPreferences.selectedModelId()?.let { modelId ->
    AiTextInferenceModel(
      id = "chatgpt:$modelId",
      name = modelId,
      contextTokens = CLOUD_PROMPT_BUDGET_CHARS,
      maxInputChars = CLOUD_PROMPT_BUDGET_CHARS,
      promptBudgetChars = CLOUD_PROMPT_BUDGET_CHARS,
      cacheVariant = "chatgpt-text-v1:$modelId",
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
    throw IllegalStateException(safeProviderMessage(error))
  } catch (error: IllegalStateException) {
    if (error.message.orEmpty().contains("利用モデルを選択")) throw error
    throw IllegalStateException("ChatGPT / Codex のテキスト生成に失敗しました")
  } catch (_: Throwable) {
    throw IllegalStateException("ChatGPT / Codex のテキスト生成に失敗しました")
  }

  private fun safeProviderMessage(error: ChatGptProviderException): String = when (error.kind) {
    ChatGptProviderFailureKind.AUTHENTICATION -> "ChatGPT の認証が無効です。設定から再ログインしてください"
    ChatGptProviderFailureKind.NOT_CONNECTED -> "ChatGPT へ接続してください"
    ChatGptProviderFailureKind.RATE_LIMITED -> "ChatGPT / Codex の利用上限またはレート制限に達しました"
    ChatGptProviderFailureKind.TRANSIENT -> "ChatGPT / Codex が一時的に利用できません"
    ChatGptProviderFailureKind.REQUEST_REJECTED -> "ChatGPT / Codex にリクエストを受け付けてもらえませんでした"
    ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED,
    ChatGptProviderFailureKind.UNKNOWN -> "ChatGPT / Codex のテキスト生成に失敗しました"
  }

  private companion object {
    const val CLOUD_PROMPT_BUDGET_CHARS = 16_000
  }
}
