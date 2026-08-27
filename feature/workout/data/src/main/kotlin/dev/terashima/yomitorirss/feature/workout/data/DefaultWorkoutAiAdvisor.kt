package dev.terashima.yomitorirss.feature.workout.data

import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.feature.workout.WorkoutAiAdvisor
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider

class DefaultWorkoutAiAdvisor(
  private val localInference: AiTextInference,
  private val cloudInference: AiTextInference,
) : WorkoutAiAdvisor {
  override suspend fun generate(provider: WorkoutAiProvider, prompt: String): String {
    val inference = when (provider) {
      WorkoutAiProvider.LOCAL -> localInference
      WorkoutAiProvider.CHATGPT -> cloudInference
    }
    return inference.generate(prompt.fitPromptBudget(inference.selectedModel()?.promptBudgetChars))
  }

  private fun String.fitPromptBudget(budget: Int?): String {
    if (budget == null || length <= budget) return this
    val marker = "\n\n[入力がモデル上限を超えたため中間の古い記録を省略]\n\n"
    val available = (budget - marker.length).coerceAtLeast(0)
    val headLength = available / 3
    val tailLength = available - headLength
    return take(headLength) + marker + takeLast(tailLength)
  }
}
