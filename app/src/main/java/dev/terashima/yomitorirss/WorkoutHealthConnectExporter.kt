package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.health.HealthExerciseSegment
import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentType
import dev.terashima.yomitorirss.feature.health.HealthWorkoutSession
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriteResult
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriter
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutExportResult
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutHistoryExporter
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import java.time.Instant
import java.time.OffsetDateTime

internal class WorkoutHealthConnectExporter(
  private val writer: HealthWorkoutWriter,
) : WorkoutHistoryExporter {
  override suspend fun export(history: WorkoutHistory): WorkoutExportResult {
    val session = history.toHealthWorkoutSession() ?: return WorkoutExportResult.FAILED
    return when (writer.writeWorkout(session)) {
      HealthWorkoutWriteResult.WRITTEN -> WorkoutExportResult.EXPORTED
      HealthWorkoutWriteResult.PERMISSION_REQUIRED -> WorkoutExportResult.PERMISSION_REQUIRED
      HealthWorkoutWriteResult.UNAVAILABLE -> WorkoutExportResult.UNAVAILABLE
    }
  }
}

internal fun WorkoutHistory.toHealthWorkoutSession(): HealthWorkoutSession? {
  val rawSegments = sets.mapNotNull(WorkoutSet::toHealthSegment).sortedBy { it.startTime }
  val segments = rawSegments.fold(mutableListOf<HealthExerciseSegment>()) { result, segment ->
    val previousEnd = result.lastOrNull()?.endTime
    val startTime = if (previousEnd != null && segment.startTime < previousEnd) previousEnd else segment.startTime
    if (startTime < segment.endTime) result += segment.copy(startTime = startTime)
    result
  }

  val finishedAt = parseWorkoutInstant(finishedAt) ?: return null
  val storedStartedAt = startedAt?.let(::parseWorkoutInstant)
  val segmentStart = segments.firstOrNull()?.startTime
  val segmentEnd = segments.lastOrNull()?.endTime
  val startTime = listOfNotNull(storedStartedAt, segmentStart).minOrNull() ?: finishedAt.minusSeconds(1)
  val endTime = listOfNotNull(finishedAt, segmentEnd).maxOrNull() ?: finishedAt
  if (startTime >= endTime) return null

  return HealthWorkoutSession(
    clientRecordId = "workout:$id",
    startTime = startTime,
    endTime = endTime,
    title = "ワークアウト",
    notes = healthConnectNotes(),
    segments = segments,
  )
}

private fun WorkoutSet.toHealthSegment(): HealthExerciseSegment? {
  val endTime = parseWorkoutInstant(finishedAt ?: recordedAt) ?: return null
  val defaultDurationSeconds = if (unit == WorkoutUnit.SECONDS) amount.coerceAtLeast(1) else 1
  val startTime = startedAt?.let(::parseWorkoutInstant) ?: endTime.minusSeconds(defaultDurationSeconds.toLong())
  if (startTime >= endTime) return null
  return HealthExerciseSegment(
    startTime = startTime,
    endTime = endTime,
    type = healthSegmentType(),
    repetitions = if (unit == WorkoutUnit.REPS) amount.coerceAtLeast(0) else 0,
  )
}

private fun WorkoutSet.healthSegmentType(): HealthExerciseSegmentType = when {
  type == WorkoutExerciseType.PLANK -> HealthExerciseSegmentType.PLANK
  type == WorkoutExerciseType.STEP_UP -> HealthExerciseSegmentType.STAIR_CLIMBING
  exerciseId == "reverse-crunch" || exerciseName.contains("クランチ") || exerciseName.contains("腹筋") -> HealthExerciseSegmentType.CRUNCH
  exerciseId == "lunge" || exerciseName.contains("ランジ") -> HealthExerciseSegmentType.LUNGE
  else -> HealthExerciseSegmentType.OTHER
}

private fun WorkoutHistory.healthConnectNotes(): String? {
  if (sets.isEmpty()) return null
  return sets.groupBy { it.exerciseId }.values.joinToString("\n") { exerciseSets ->
    val exercise = exerciseSets.first()
    val total = exerciseSets.sumOf { it.amount }
    buildString {
      append(exercise.exerciseName)
      append(": ")
      append(exerciseSets.size)
      append("セット / ")
      if (exercise.unit == WorkoutUnit.REPS) {
        append(total)
        append("回")
      } else {
        append(total)
        append("秒")
      }
      val steps = exerciseSets.sumOf { it.steps ?: 0 }
      if (steps > 0) {
        append(" / ")
        append(steps)
        append("段")
      }
    }
  }.take(1000)
}

private fun parseWorkoutInstant(value: String): Instant? =
  runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    ?: runCatching { Instant.parse(value) }.getOrNull()
