package dev.terashima.yomitorirss.feature.workout.data

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutExportResult
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectWorkoutHistoryExporterTest {
  @Test
  fun `ワークアウト履歴をHealth Connect recordへ変換する`() {
    val history = workoutHistory()

    val record = requireNotNull(history.toHealthConnectExerciseSessionRecord())

    assertEquals("workout:history-1", record.metadata.clientRecordId)
    assertEquals(Instant.parse("2026-08-20T01:00:00Z"), record.startTime)
    assertEquals(Instant.parse("2026-08-20T01:10:00Z"), record.endTime)
    assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, record.exerciseType)
    assertEquals(
      listOf(
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING,
      ),
      record.segments.map { it.segmentType },
    )
    assertEquals(listOf(10, 12, 16, 0, 0), record.segments.map { it.repetitions })
    assertTrue(record.notes?.contains("踏み台昇降: 1セット / 60秒 / 80段") == true)
  }

  @Test
  fun `重なるセット時刻をHealth Connectの制約に合わせて補正する`() {
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

    val segments = requireNotNull(history.toHealthConnectExerciseSessionRecord()).segments

    assertEquals(2, segments.size)
    assertEquals(segments[0].endTime, segments[1].startTime)
  }

  @Test
  fun `書込権限はExerciseSessionだけに限定する`() {
    assertEquals(
      setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class)),
      HealthConnectWorkoutHistoryExporter.WRITE_PERMISSIONS,
    )
  }

  @Test
  fun `権限未許可をWorkout側の結果へ変換する`() = runBlocking {
    val gateway = FakeGateway(available = true, permitted = false)
    val exporter = HealthConnectWorkoutHistoryExporter(gateway)

    assertEquals(WorkoutExportResult.PERMISSION_REQUIRED, exporter.export(workoutHistory()))
    assertEquals(null, gateway.lastRecord)
  }

  @Test
  fun `利用不可をWorkout側の結果へ変換する`() = runBlocking {
    val gateway = FakeGateway(available = false, permitted = true)
    val exporter = HealthConnectWorkoutHistoryExporter(gateway)

    assertEquals(WorkoutExportResult.UNAVAILABLE, exporter.export(workoutHistory()))
    assertEquals(null, gateway.lastRecord)
  }

  @Test
  fun `書込成功時にrecordをgatewayへ渡す`() = runBlocking {
    val gateway = FakeGateway(available = true, permitted = true)
    val exporter = HealthConnectWorkoutHistoryExporter(gateway)

    assertEquals(WorkoutExportResult.EXPORTED, exporter.export(workoutHistory()))
    assertNotNull(gateway.lastRecord)
  }

  @Test
  fun `gateway書込失敗をFAILEDへ変換する`() = runBlocking {
    val gateway = FakeGateway(available = true, permitted = true, failInsert = true)
    val exporter = HealthConnectWorkoutHistoryExporter(gateway)

    assertEquals(WorkoutExportResult.FAILED, exporter.export(workoutHistory()))
    assertNotNull(gateway.lastRecord)
  }

  private fun workoutHistory(): WorkoutHistory = WorkoutHistory(
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

  private fun set(
    exerciseId: String,
    exerciseName: String,
    type: WorkoutExerciseType,
    unit: WorkoutUnit,
    amount: Int,
    finishedAt: String,
    steps: Int? = null,
  ): WorkoutSet {
    val end = OffsetDateTime.parse(finishedAt)
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

  private class FakeGateway(
    private val available: Boolean,
    private val permitted: Boolean,
    private val failInsert: Boolean = false,
  ) : WorkoutHealthConnectGateway {
    var lastRecord: ExerciseSessionRecord? = null

    override fun isAvailable(): Boolean = available

    override suspend fun hasWritePermission(): Boolean = permitted

    override suspend fun insert(record: ExerciseSessionRecord) {
      lastRecord = record
      if (failInsert) error("simulated write failure")
    }
  }
}
