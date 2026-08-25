package dev.terashima.yomitorirss.core.airuntime

import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceStage
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAiTextInferenceTest {
  @Test
  fun `ローカルモデル能力を共通テキスト推論モデルへ投影する`() {
    val model = LocalModelStatus(
      id = "local-a",
      name = "Local A",
      description = "test",
      source = "test",
      license = "test",
      quantization = "test",
      sizeBytes = 1,
      downloadedBytes = 1,
      downloaded = true,
      selected = true,
      recommended = true,
      memoryLow = false,
      supportsThinking = false,
      supportsSpeculativeDecoding = false,
      contextTokens = 8_192,
      maxInputChars = 24_000,
      promptBudgetChars = 20_000,
      promptFormat = LocalPromptFormat.PLAIN,
    )

    val mapped = model.toAiTextInferenceModel("local:local-a:context-8192")

    assertEquals("local-a", mapped.id)
    assertEquals("Local A", mapped.name)
    assertEquals(8_192, mapped.contextTokens)
    assertEquals(24_000, mapped.maxInputChars)
    assertEquals(20_000, mapped.promptBudgetChars)
    assertEquals("local:local-a:context-8192", mapped.cacheIdentity)
  }

  @Test
  fun `ローカル推論進捗を共通ステージへ投影する`() {
    val preparing = LocalInferenceProgress(
      stage = LocalInferenceStage.PREPARING_MODEL,
      modelName = "Local A",
      estimatedStageDurationMillis = 1_000,
    ).toAiTextInferenceProgress()
    val generating = LocalInferenceProgress(
      stage = LocalInferenceStage.GENERATING_RESPONSE,
      modelName = "Local A",
      estimatedStageDurationMillis = 2_000,
    ).toAiTextInferenceProgress()

    assertEquals(AiTextInferenceStage.PREPARING_MODEL, preparing.stage)
    assertEquals(AiTextInferenceStage.GENERATING_RESPONSE, generating.stage)
    assertEquals("Local A", generating.modelName)
    assertEquals(2_000L, generating.estimatedStageDurationMillis)
  }
}
