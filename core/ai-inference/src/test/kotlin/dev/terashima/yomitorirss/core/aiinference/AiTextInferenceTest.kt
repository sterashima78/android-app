package dev.terashima.yomitorirss.core.aiinference

import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AiTextInferenceTest {
  @Test
  fun `推論モデルは実行に必要な能力とキャッシュvariantを保持する`() {
    val model = AiTextInferenceModel(
      id = "model-a",
      name = "Model A",
      contextTokens = 8_192,
      maxInputChars = 24_000,
      promptBudgetChars = 20_000,
      cacheVariant = "variant-1",
    )

    assertEquals("model-a", model.id)
    assertEquals(8_192, model.contextTokens)
    assertEquals(20_000, model.promptBudgetChars)
    assertEquals("variant-1", model.cacheVariant)
  }

  @Test
  fun `background scope外ではtext生成実装を呼び出さない`() = runBlocking {
    var generated = false
    val inference = object : BackgroundAiTextInference() {
      override val progress: Flow<AiTextInferenceProgress?> = emptyFlow()
      override fun selectedModel(): AiTextInferenceModel? = null
      override fun countTokens(text: String): Int = text.length

      override suspend fun generateInBackground(prompt: String): String {
        generated = true
        return prompt
      }
    }

    assertThrows(IllegalStateException::class.java) {
      runBlocking { inference.generate("prompt") }
    }
    assertFalse(generated)
  }

  @Test
  fun `推論モデルは空のキャッシュvariantを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      AiTextInferenceModel(
        id = "model-a",
        name = "Model A",
        contextTokens = 8_192,
        maxInputChars = 24_000,
        promptBudgetChars = 20_000,
        cacheVariant = "",
      )
    }
  }
}
