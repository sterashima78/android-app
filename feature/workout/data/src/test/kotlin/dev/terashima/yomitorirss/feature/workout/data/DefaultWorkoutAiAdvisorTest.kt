package dev.terashima.yomitorirss.feature.workout.data

import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWorkoutAiAdvisorTest {
  @Test
  fun `選択したproviderだけを実行する`() = runBlocking {
    val local = RecordingInference()
    val cloud = RecordingInference()
    val advisor = DefaultWorkoutAiAdvisor(local, cloud)

    assertEquals("response", advisor.generate(WorkoutAiProvider.CHATGPT, "prompt"))

    assertTrue(local.prompts.isEmpty())
    assertEquals(listOf("prompt"), cloud.prompts)
  }

  @Test
  fun `prompt budget超過時は冒頭と末尾を残して中間を省略する`() = runBlocking {
    val local = RecordingInference(promptBudgetChars = 120)
    val cloud = RecordingInference()
    val advisor = DefaultWorkoutAiAdvisor(local, cloud)
    val prompt = "SAFETY:" + "x".repeat(180) + ":CURRENT_REQUEST"

    advisor.generate(WorkoutAiProvider.LOCAL, prompt)

    val actual = local.prompts.single()
    assertTrue(actual.length <= 120)
    assertTrue(actual.startsWith("SAFETY:"))
    assertTrue(actual.endsWith(":CURRENT_REQUEST"))
    assertTrue(actual.contains("入力がモデル上限を超えたため中間の古い記録を省略"))
  }

  private class RecordingInference(
    private val promptBudgetChars: Int = 16_000,
  ) : AiTextInference {
    val prompts = mutableListOf<String>()

    override val progress: Flow<AiTextInferenceProgress?> = emptyFlow()

    override fun selectedModel(): AiTextInferenceModel = AiTextInferenceModel(
      id = "test",
      name = "test",
      contextTokens = 4_096,
      maxInputChars = promptBudgetChars,
      promptBudgetChars = promptBudgetChars,
      cacheVariant = "test",
    )

    override fun countTokens(text: String): Int = text.length

    override suspend fun generate(prompt: String): String {
      prompts += prompt
      return "response"
    }
  }
}
