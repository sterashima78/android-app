package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate

enum class WorkoutAiProvider {
  LOCAL,
  CHATGPT,
}

enum class WorkoutAiRequestType {
  MENU_SUGGESTION,
  POST_WORKOUT_REVIEW,
}

data class WorkoutAiSettings(
  val provider: WorkoutAiProvider = WorkoutAiProvider.LOCAL,
  val workoutPolicy: String = "",
  // Legacy persisted value. Menu candidates are now derived from WorkoutSnapshot.exercises.
  val menuCandidates: String = "",
)

interface WorkoutAiSettingsRepository {
  suspend fun loadSettings(): WorkoutAiSettings
  suspend fun saveSettings(settings: WorkoutAiSettings)
  suspend fun loadMemo(date: String): String
  suspend fun saveMemo(date: String, memo: String)
  suspend fun loadMemos(dates: Set<String>): Map<String, String>
}

interface WorkoutAiAdvisor {
  suspend fun generate(provider: WorkoutAiProvider, prompt: String): String
}

object WorkoutAiPromptBuilder {
  private const val HISTORY_DAYS = 14L

  fun build(
    type: WorkoutAiRequestType,
    snapshot: WorkoutSnapshot,
    settings: WorkoutAiSettings,
    memos: Map<String, String>,
    today: LocalDate = LocalDate.now(),
  ): String {
    val since = today.minusDays(HISTORY_DAYS - 1)
    val recentHistory = snapshot.history
      .filter { history -> history.date.toLocalDateOrNull()?.let { !it.isBefore(since) && !it.isAfter(today) } == true }
      .sortedBy { it.date }
    val todayDate = today.toString()
    val pastHistory = recentHistory.filterNot { it.date == todayDate }
    val todaySets = buildList {
      recentHistory.filter { it.date == todayDate }.forEach { addAll(it.sets) }
      if (snapshot.today.date == todayDate) addAll(snapshot.today.sets)
    }.distinctBy { it.id }

    return buildString {
      appendLine("あなたは筋力トレーニングの記録を読み、実行可能な提案を返すアシスタントです。")
      appendLine("医療診断は行わず、痛み・強い不調・異常が記載されている場合は無理な運動を勧めないでください。")
      appendLine("入力にない重量・回数・体調を事実として補完しないでください。")
      appendLine()
      appendLine("## ワークアウト方針")
      appendLine(settings.workoutPolicy.ifBlank { "未設定" })
      appendLine()
      appendLine("## 設定済みトレーニングメニュー")
      if (snapshot.exercises.isEmpty()) {
        appendLine("未設定")
      } else {
        snapshot.exercises.forEach { exercise ->
          appendLine("- ${exercise.name}: 目標 ${exercise.targetSets}セット / 単位 ${exercise.unit.label}")
        }
      }
      appendLine()
      appendLine("## 直近14日間の過去記録")
      if (pastHistory.isEmpty()) {
        appendLine("履歴なし")
      } else {
        pastHistory.forEach { history ->
          appendHistory(history, memos[history.date])
        }
      }
      appendLine()
      appendLine("## 今日 $today")
      appendLine("ワークアウトメモ: ${memos[todayDate].orEmpty().ifBlank { "なし" }}")
      if (todaySets.isEmpty()) {
        appendLine("記録済みセット: なし")
      } else {
        appendLine("記録済みセット:")
        todaySets.forEach { appendLine("- ${formatSetForAi(it)}") }
      }
      appendLine()
      when (type) {
        WorkoutAiRequestType.MENU_SUGGESTION -> {
          appendLine("## 依頼")
          appendLine("今日行うメニューを提案してください。種目ごとにセット数と1セットあたりの回数または秒数を明示してください。")
          appendLine("直近14日間の実績、今日のメモ、ワークアウト方針、設定済みトレーニングメニューを優先して調整してください。")
          appendLine("最後に提案理由を短く記載してください。")
        }
        WorkoutAiRequestType.POST_WORKOUT_REVIEW -> {
          appendLine("## 依頼")
          appendLine("今日のワークアウトをレビューしてください。実績とメモを根拠に、良かった点、負荷の評価、次回の調整案を返してください。")
          appendLine("今日の記録が不足している場合は、不足していることを明示し、断定的な評価を避けてください。")
        }
      }
    }.trim()
  }

  fun recentDates(snapshot: WorkoutSnapshot, today: LocalDate = LocalDate.now()): Set<String> {
    val since = today.minusDays(HISTORY_DAYS - 1)
    return buildSet {
      add(today.toString())
      snapshot.history.forEach { history ->
        val date = history.date.toLocalDateOrNull() ?: return@forEach
        if (!date.isBefore(since) && !date.isAfter(today)) add(history.date)
      }
    }
  }

  private fun StringBuilder.appendHistory(history: WorkoutHistory, memo: String?) {
    appendLine("### ${history.date}")
    appendLine("メモ: ${memo.orEmpty().ifBlank { "なし" }}")
    history.sets.forEach { appendLine("- ${formatSetForAi(it)}") }
  }

  private fun formatSetForAi(set: WorkoutSet): String = buildString {
    append(set.exerciseName)
    append(": ${set.amount}${set.unit.label}")
    set.steps?.let { append(" / ${it}段") }
    if (set.memo.isNotBlank()) append(" / セットメモ: ${set.memo}")
  }

  private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
}
