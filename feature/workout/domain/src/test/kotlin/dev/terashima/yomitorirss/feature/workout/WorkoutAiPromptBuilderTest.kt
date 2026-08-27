package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAiPromptBuilderTest {
  private val today = LocalDate.of(2026, 8, 27)

  @Test
  fun `メニュー提案には直近14日と方針と設定済みメニューとメモを含める`() {
    val snapshot = snapshotWithHistory()
    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.MENU_SUGGESTION,
      snapshot = snapshot,
      settings = WorkoutAiSettings(workoutPolicy = "継続を優先する"),
      memos = mapOf(
        "2026-08-27" to "今日は少し疲れている",
        "2026-08-14" to "調子が良い",
        "2026-08-13" to "14日より前",
      ),
      today = today,
    )

    assertTrue(prompt.contains("継続を優先する"))
    assertTrue(prompt.contains("設定済みトレーニングメニュー"))
    assertTrue(prompt.contains("腕立て伏せ: 目標 3セット / 単位 回"))
    assertTrue(prompt.contains("今日は少し疲れている"))
    assertTrue(prompt.contains("2026-08-14"))
    assertFalse(prompt.contains("2026-08-13"))
    assertTrue(prompt.contains("セット数と1セットあたりの回数または秒数"))
  }

  @Test
  fun `完了後レビューには進行中の当日実績を含める`() {
    val snapshot = snapshotWithHistory().copy(
      today = WorkoutDay(
        date = "2026-08-27",
        sets = listOf(workoutSet("today", "腕立て伏せ", 12)),
      ),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = mapOf("2026-08-27" to "最後のセットがきつかった"),
      today = today,
    )

    assertTrue(prompt.contains("腕立て伏せ: 12回"))
    assertTrue(prompt.contains("最後のセットがきつかった"))
    assertTrue(prompt.contains("良かった点、負荷の評価、次回の調整案"))
  }

  @Test
  fun `当日完了済み履歴のセットを今日の記録済みセットとして含める`() {
    val snapshot = snapshotWithHistory().copy(
      today = WorkoutDay(date = "2026-08-27"),
      history = listOf(
        workoutHistory("2026-08-27", workoutSet("completed-today", "プランク", 60, WorkoutUnit.SECONDS)),
        workoutHistory("2026-08-14"),
      ),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      today = today,
    )

    val todaySection = prompt.substringAfter("## 今日 2026-08-27")
    assertTrue(todaySection.contains("記録済みセット:"))
    assertTrue(todaySection.contains("プランク: 60秒"))
    assertFalse(todaySection.contains("記録済みセット: なし"))
    assertFalse(prompt.substringBefore("## 今日 2026-08-27").contains("### 2026-08-27"))
  }

  @Test
  fun `recentDates は今日と直近14日だけを返す`() {
    val dates = WorkoutAiPromptBuilder.recentDates(snapshotWithHistory(), today)

    assertTrue("2026-08-27" in dates)
    assertTrue("2026-08-14" in dates)
    assertFalse("2026-08-13" in dates)
  }

  private fun snapshotWithHistory(): WorkoutSnapshot = newWorkoutSnapshot(today.toString()).copy(
    history = listOf(
      workoutHistory("2026-08-14"),
      workoutHistory("2026-08-13"),
    ),
  )

  private fun workoutHistory(
    date: String,
    set: WorkoutSet = workoutSet(date, "ランジ", 10),
  ) = WorkoutHistory(
    id = date,
    date = date,
    startedAt = null,
    finishedAt = "${date}T08:00:00+09:00",
    sets = listOf(set),
  )

  private fun workoutSet(
    id: String,
    name: String,
    amount: Int,
    unit: WorkoutUnit = WorkoutUnit.REPS,
  ) = WorkoutSet(
    id = id,
    exerciseId = "exercise-$id",
    exerciseName = name,
    unit = unit,
    type = if (unit == WorkoutUnit.SECONDS) WorkoutExerciseType.TIMED else WorkoutExerciseType.REPS,
    amount = amount,
    recordedAt = "2026-08-27T08:00:00+09:00",
  )
}
