package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentType
import dev.terashima.yomitorirss.feature.health.HealthWorkoutSession
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriteResult
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriter
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutExportResult
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkoutHealthConnectExporterTest {
  @Test
  fun `ワークアウト履歴をHealth Connect用セッションへ変換する`() {
    val history = WorkoutHistory(
      id = "history-1",
      date = "2026-08-20",
      startedAt = "2026-08-20T10:00:00+09:00",
      finishedAt = "2026-08-20T10:10:00+09:00",
      sets = listOf(
        set("push-up", "腕立て伏せ", WorkoutExerciseType.REPS, WorkoutUnit.REPS, 10, "2026-08-20T10:01:00+09:00"),
        set("reverse-crunch", "リバースクランチ", WorkoutExerciseType.REPS, WorkoutUnit.REPS, 12, "2026-08-20T10:02:00+09:00"),
        set("lunge", "ランジ", WorkoutExerciseType.REPS, WorkoutUnit.REPS, 16, "2026-08-20T10:03:00+09:00"),
        set("plank", "プランク", WorkoutExerciseType.PLANK, WorkoutUnit.SECONDS, 30, "2026-08-20T10:04:00+09:00"),
        set("step-up", "踏み台昇降", WorkoutExerciseType.STEP_UP, WorkoutUnit.SECONDS, 60, "2026-08-20T10:06:00+09:00", steps = 80),
      ),
    )

    val session = assertNotNull(history.toHealthWorkoutSession()) as HealthWorkoutSession

    assertEquals("workout:history-1", session.clientRecordId)
    assertEquals(Instant.parse("2026-08-20T01:00:00Z"), session.startTime)
    assertEquals(Instant.parse("2026-08-20T01:10:00Z"), session.endTime)
    assertEquals(
      listOf(
        HealthExerciseSegmentType.OTHER,
        HealthExerciseSegmentType.CRUNCH,
        HealthExerciseSegmentType.LUNGE,
        HealthExerciseSegmentType.PLANK,
        HealthExerciseSegmentType.STAIR_CLIMBING,
      ),
      session.segments.map { it.type },
    )
    assertEquals(listOf(10, 12, 16, 0, 0), session.segments.map { it.repetitions })
    assertEquals(true, session.notes?.contains("踏み台昇降: 1セット / 60秒 / 80段"))
  }

  @Test
  fun `重なるセット時刻をHealth Connectの制約に合わせて重ならないよう補正する`() {
    val history = WorkoutHistory(
      id = "overlap",
      date = "2026-08-20",
      startedAt = "2026-08-20T10:00:00+09:00",
      finishedAt = "2026-08-20T10:05:00+09:00",
      sets = listOf(
        set("plank", "プランク", WorkoutExerciseType.PLANK, WorkoutUnit.SECONDS, 60, "2026-08-20T10:02:00+09:00"),
        set("step-up", "踏み台昇降", WorkoutExerciseType.STEP_UP, WorkoutUnit.SECONDS, 90, "2026-08-20T10:02:30+09:00"),
      ),
    )

    val segments = requireNotNull(history.toHealthWorkoutSession()).segments

    assertEquals(2, segments.size)
    assertEquals(segments[0].endTime, segments[1].startTime)
  }

  @Test
  fun `Health writerの結果をWorkout側の結果へ変換する`() = runBlocking {
    val writer = FakeWriter(HealthWorkoutWriteResult.PERMISSION_REQUIRED)
    val exporter = WorkoutHealthConnectExporter(writer)
    val history = WorkoutHistory(
      id = "permission",
      date = "2026-08-20",
      startedAt = "2026-08-20T10:00:00+09:00",
      finishedAt = "2026-08-20T10:01:00+09:00",
      sets = listOf(
        set("push-up", "腕立て伏せ", WorkoutExerciseType.REPS, WorkoutUnit.REPS, 10, "2026-08-20T10:00:30+09:00"),
      ),
    )

    assertEquals(WorkoutExportResult.PERMISSION_REQUIRED, exporter.export(history))
    assertNotNull(writer.lastSession)
  }

  private fun set(
    exerciseId: String,
    exerciseName: String,
    type: WorkoutExerciseType,
    unit: WorkoutUnit,
    amount: Int,
    finishedAt: String,
    steps: Int? = null,
  ): WorkoutSet {
    val end = java.time.OffsetDateTime.parse(finishedAt)
    val duration = if (unit == WorkoutUnit.SECONDS) amount.toLong() else 1L
    return WorkoutSet(
      id = "$exerciseId-$finishedAt",
      exerciseId = exerciseId,
      exerciseName = exerciseName,
      unit = unit,
      type = type,
      amount = amount,
      steps = steps,
      recordedAt = finishedAt,
      startedAt = end.minusSeconds(duration).toString(),
      finishedAt = finishedAt,
    )
  }

  private class FakeWriter(
    private val result: HealthWorkoutWriteResult,
  ) : HealthWorkoutWriter {
    var lastSession: HealthWorkoutSession? = null

    override suspend fun writeWorkout(session: HealthWorkoutSession): HealthWorkoutWriteResult {
      lastSession = session
      return result
    }
  }
}
