package dev.terashima.yomitorirss.feature.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryCopyTextTest {
  @Test
  fun `履歴の表示内容をコピー用テキストに整形する`() {
    val history = WorkoutHistory(
      id = "history-1",
      date = "2026-08-19",
      startedAt = "2026-08-19T08:00:00+09:00",
      finishedAt = "2026-08-19T08:30:00+09:00",
      sets = listOf(
        workoutSet("push-1", "push-up", "腕立て伏せ", WorkoutUnit.REPS, 10),
        workoutSet("push-2", "push-up", "腕立て伏せ", WorkoutUnit.REPS, 12),
        workoutSet("step-1", "step-up", "踏み台昇降", WorkoutUnit.SECONDS, 90, steps = 120),
      ),
    )

    assertEquals(
      "2026-08-19\n3 セット\n腕立て伏せ: 2セット / 22回\n踏み台昇降: 1セット / 01:30 / 120段",
      formatWorkoutHistoryForCopy(history),
    )
  }

  @Test
  fun `1時間以上の時間は時分秒で整形する`() {
    val history = WorkoutHistory(
      id = "history-2",
      date = "2026-08-18",
      startedAt = null,
      finishedAt = "2026-08-18T10:30:00+09:00",
      sets = listOf(
        workoutSet("plank-1", "plank", "プランク", WorkoutUnit.SECONDS, 3661),
      ),
    )

    assertEquals(
      "2026-08-18\n1 セット\nプランク: 1セット / 1:01:01",
      formatWorkoutHistoryForCopy(history),
    )
  }

  private fun workoutSet(
    id: String,
    exerciseId: String,
    exerciseName: String,
    unit: WorkoutUnit,
    amount: Int,
    steps: Int? = null,
  ) = WorkoutSet(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    unit = unit,
    type = if (exerciseId == "step-up") WorkoutExerciseType.STEP_UP else WorkoutExerciseType.REPS,
    amount = amount,
    steps = steps,
    recordedAt = "2026-08-19T08:00:00+09:00",
  )
}
