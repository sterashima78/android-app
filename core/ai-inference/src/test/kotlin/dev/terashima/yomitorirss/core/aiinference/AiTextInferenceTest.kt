package dev.terashima.yomitorirss.core.aiinference

import org.junit.Assert.assertEquals
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
