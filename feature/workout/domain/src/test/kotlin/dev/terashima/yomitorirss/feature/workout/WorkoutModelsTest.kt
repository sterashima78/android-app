package dev.terashima.yomitorirss.feature.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutModelsTest {
  @Test
  fun `任意詳細は記録されている値だけを表示する`() {
    val original = WorkoutSet(
      id = "s1",
      exerciseId = "pull",
      exerciseName = "斜め懸垂",
      unit = WorkoutUnit.REPS,
      type = WorkoutExerciseType.REPS,
      amount = 8,
      recordedAt = "2026-10-09T08:00:00+09:00",
      rpe = 8,
      formQuality = WorkoutFormQuality.UNSTABLE,
      load = WorkoutLoad(WorkoutLoadKind.BODY_ANGLE, "中程度の傾斜"),
      restSeconds = 90,
    )
    assertEquals(
      "RPE 8/10 / フォーム: 途中から崩れた / 身体の角度: 中程度の傾斜 / 直前の休憩: 90秒",
      formatWorkoutSetDetails(original),
    )
    assertEquals("", formatWorkoutSetDetails(original.copy(rpe = null, formQuality = null, load = null, restSeconds = null)))
  }

  @Test
  fun `異常な運動強度や重量は受け付けない`() {
    val base = WorkoutSet(
      id = "s1",
      exerciseId = "pull",
      exerciseName = "斜め懸垂",
      unit = WorkoutUnit.REPS,
      type = WorkoutExerciseType.REPS,
      amount = 8,
      recordedAt = "2026-10-09T08:00:00+09:00",
    )
    assertTrue(runCatching { base.copy(rpe = 11) }.isFailure)
    assertTrue(runCatching { base.copy(restSeconds = -1) }.isFailure)
    assertTrue(runCatching { WorkoutLoad(WorkoutLoadKind.ADDED_WEIGHT, "-1") }.isFailure)
    assertTrue(runCatching { WorkoutLoad(WorkoutLoadKind.ADDED_WEIGHT, "invalid") }.isFailure)
  }

  @Test
  fun `日付変更時に当日の記録を履歴へ移す`() {
    val base = newWorkoutSnapshot("2026-08-09")
    val exercise = base.exercises.first()
    val snapshot = base.copy(
      today = base.today.copy(
        startedAt = "2026-08-09T10:00:00+09:00",
        sets = listOf(
          WorkoutSet(
            id = "set-1",
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            unit = exercise.unit,
            type = exercise.type,
            amount = 10,
            recordedAt = "2026-08-09T10:01:00+09:00",
          ),
        ),
      ),
    )

    val rolled = snapshot.rolloverTo("2026-08-10", "2026-08-10T08:00:00+09:00")

    assertEquals("2026-08-10", rolled.today.date)
    assertTrue(rolled.today.sets.isEmpty())
    assertEquals(1, rolled.history.size)
    assertEquals(10, rolled.history.first().sets.first().amount)
    assertEquals("基本メニュー", rolled.history.first().menu?.name)
  }

  @Test
  fun `履歴は最大50件に制限する`() {
    val base = newWorkoutSnapshot("2026-08-09")
    val oldHistory = (1..50).map {
      WorkoutHistory("h$it", "2026-08-${it.toString().padStart(2, '0')}", null, "done$it", emptyList())
    }
    val exercise = base.exercises.first()
    val rolled = base.copy(
      history = oldHistory,
      today = base.today.copy(
        sets = listOf(
          WorkoutSet("set", exercise.id, exercise.name, exercise.unit, exercise.type, 1, recordedAt = "now"),
        ),
      ),
    ).rolloverTo("2026-08-10", "finished")

    assertEquals(50, rolled.history.size)
    assertEquals("2026-08-09-finished", rolled.history.first().id)
  }

  @Test
  fun `基本メニューは種目の既存セット数から生成する`() {
    val snapshot = newWorkoutSnapshot("2026-09-16")

    assertEquals("基本メニュー", snapshot.effectiveMenu().name)
    assertEquals(snapshot.exercises.size, snapshot.effectiveMenu().items.size)
    snapshot.exercises.zip(snapshot.effectiveMenu().items).forEach { (exercise, item) ->
      assertEquals(exercise.id, item.exerciseId)
      assertEquals(exercise.targetSets, item.targetSets)
    }
  }

  @Test
  fun `当日メニューのセット数を種目表示へ投影する`() {
    val snapshot = newWorkoutSnapshot("2026-09-16")
    val exercise = snapshot.exercises.first()
    val menu = WorkoutMenu(
      id = "short",
      name = "短時間",
      items = listOf(WorkoutMenuItem(exercise.id, targetSets = 2, targets = listOf(8, 6))),
    )

    val adjusted = snapshot.copy(today = snapshot.today.copy(menu = menu))

    assertEquals(2, adjusted.menuExercises().single().targetSets)
    assertEquals(listOf(8, 6), adjusted.menuItem(exercise.id)?.targets)
  }
}
