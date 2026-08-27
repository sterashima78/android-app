package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAiPromptBuilderTest {
  private val today = LocalDate.of(2026, 8, 27)

  @Test
  fun `メニュー提案には直近14日と方針とメモを含める`() {
    val snapshot = snapshotWithHistory()
    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.MENU_SUGGESTION,
      snapshot = snapshot,
      settings = WorkoutAiSettings(
        workoutPolicy = "継続を優先する",
        menuCandidates = "腕立て伏せ、ランジ",
      ),
      memos = mapOf(
        "2026-08-27" to "今日は少し疲れている",
        "2026-08-14" to "調子が良い",
        "2026-08-13" to "14日より前",
      ),
      today = today,
    )

    assertTrue(prompt.contains("継続を優先する"))
    assertTrue(prompt.contains("腕立て伏せ、ランジ"))
    assertTrue(prompt.contains("今日は少し疲れている"))
    assertTrue(prompt.contains("2026-08-14"))
    assertFalse(prompt.contains("2026-08-13"))
    assertTrue(prompt.contains("セット数と1セットあたりの回数または秒数"))
  }

  @Test
  fun `完了後レビューには当日の実績を含める`() {
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

  private fun workoutHistory(date: String) = WorkoutHistory(
    id = date,
    date = date,
    startedAt = null,
    finishedAt = "${date}T08:00:00+09:00",
    sets = listOf(workoutSet(date, "ランジ", 10)),
  )

  private fun workoutSet(id: String, name: String, amount: Int) = WorkoutSet(
    id = id,
    exerciseId = "exercise-$id",
    exerciseName = name,
    unit = WorkoutUnit.REPS,
    type = WorkoutExerciseType.REPS,
    amount = amount,
    recordedAt = "2026-08-27T08:00:00+09:00",
  )
}
