package dev.terashima.yomitorirss.core.aicloudopenai

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import kotlinx.coroutines.CancellationException

class ChatGptStructuredTextInference(
  private val client: ChatGptInferenceClient,
  private val modelPreferences: ChatGptModelPreferences,
) : AiStructuredTextInference {
  override suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? = try {
    val modelId = modelPreferences.selectedModelId()
      ?: error("クラウドAIの利用モデルを選択してください")
    client.generateToolCall(
      modelId = modelId,
      systemInstruction = systemInstruction,
      userMessage = userMessage,
      tool = tool,
    )
  } catch (error: CancellationException) {
    throw error
  } catch (error: ChatGptProviderException) {
    throw IllegalStateException(safeStructuredProviderMessage(error))
  } catch (error: IllegalStateException) {
    if (error.message.orEmpty().contains("利用モデルを選択")) throw error
    throw IllegalStateException("クラウドAIの構造化生成に失敗しました")
  } catch (_: Throwable) {
    throw IllegalStateException("クラウドAIの構造化生成に失敗しました")
  }

  private fun safeStructuredProviderMessage(error: ChatGptProviderException): String = when (error.kind) {
    ChatGptProviderFailureKind.AUTHENTICATION -> "クラウドAIの認証が無効です。設定から再ログインしてください"
    ChatGptProviderFailureKind.NOT_CONNECTED -> "クラウドAIへ接続してください"
    ChatGptProviderFailureKind.RATE_LIMITED -> "クラウドAIの利用上限またはレート制限に達しました"
    ChatGptProviderFailureKind.TRANSIENT -> "クラウドAIが一時的に利用できません"
    ChatGptProviderFailureKind.REQUEST_REJECTED -> "クラウドAIにリクエストを受け付けてもらえませんでした"
    ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED,
    ChatGptProviderFailureKind.UNKNOWN -> "クラウドAIの構造化生成に失敗しました"
  }
}
