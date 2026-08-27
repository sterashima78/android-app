package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutExportResult
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutHistoryExporter
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class HealthConnectWorkoutHistoryExporter internal constructor(
  private val gateway: WorkoutHealthConnectGateway,
) : WorkoutHistoryExporter {
  constructor(context: Context) : this(AndroidWorkoutHealthConnectGateway(context.applicationContext))

  override suspend fun export(history: WorkoutHistory): WorkoutExportResult {
    val record = history.toHealthConnectExerciseSessionRecord() ?: return WorkoutExportResult.FAILED
    if (!gateway.isAvailable()) return WorkoutExportResult.UNAVAILABLE
    if (!gateway.hasWritePermission()) return WorkoutExportResult.PERMISSION_REQUIRED
    return runCatching {
      gateway.insert(record)
      WorkoutExportResult.EXPORTED
    }.getOrDefault(WorkoutExportResult.FAILED)
  }

  companion object {
    val WRITE_PERMISSIONS: Set<String> = setOf(
      HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )
  }
}

internal interface WorkoutHealthConnectGateway {
  fun isAvailable(): Boolean
  suspend fun hasWritePermission(): Boolean
  suspend fun insert(record: ExerciseSessionRecord)
}

private class AndroidWorkoutHealthConnectGateway(
  private val context: Context,
) : WorkoutHealthConnectGateway {
  private val client: HealthConnectClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HealthConnectClient.getOrCreate(context)
  }

  override fun isAvailable(): Boolean =
    HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

  override suspend fun hasWritePermission(): Boolean =
    client.permissionController.getGrantedPermissions()
      .containsAll(HealthConnectWorkoutHistoryExporter.WRITE_PERMISSIONS)

  override suspend fun insert(record: ExerciseSessionRecord) {
    client.insertRecords(listOf(record))
  }
}

internal fun WorkoutHistory.toHealthConnectExerciseSessionRecord(): ExerciseSessionRecord? {
  val rawSegments = sets.mapNotNull(WorkoutSet::toHealthConnectSegment).sortedBy(ExerciseSegment::startTime)
  val segments = rawSegments.fold(mutableListOf<ExerciseSegment>()) { result, segment ->
    val previousEnd = result.lastOrNull()?.endTime
    val startTime = if (previousEnd != null && segment.startTime < previousEnd) previousEnd else segment.startTime
    if (startTime < segment.endTime) {
      result += ExerciseSegment(
        startTime = startTime,
        endTime = segment.endTime,
        segmentType = segment.segmentType,
        repetitions = segment.repetitions,
      )
    }
    result
  }

  val finishedAt = parseWorkoutInstant(finishedAt) ?: return null
  val storedStartedAt = startedAt?.let(::parseWorkoutInstant)
  val segmentStart = segments.firstOrNull()?.startTime
  val segmentEnd = segments.lastOrNull()?.endTime
  val startTime = listOfNotNull(storedStartedAt, segmentStart).minOrNull() ?: finishedAt.minusSeconds(1)
  val endTime = listOfNotNull(finishedAt, segmentEnd).maxOrNull() ?: finishedAt
  if (startTime >= endTime) return null

  val zone = ZoneId.systemDefault().rules
  return ExerciseSessionRecord(
    startTime = startTime,
    startZoneOffset = zone.getOffset(startTime),
    endTime = endTime,
    endZoneOffset = zone.getOffset(endTime),
    metadata = Metadata.manualEntry(clientRecordId = "workout:$id"),
    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
    title = "ワークアウト",
    notes = healthConnectNotes(),
    segments = segments,
  )
}

private fun WorkoutSet.toHealthConnectSegment(): ExerciseSegment? {
  val endTime = parseWorkoutInstant(finishedAt ?: recordedAt) ?: return null
  val defaultDurationSeconds = if (unit == WorkoutUnit.SECONDS) amount.coerceAtLeast(1) else 1
  val startTime = startedAt?.let(::parseWorkoutInstant) ?: endTime.minusSeconds(defaultDurationSeconds.toLong())
  if (startTime >= endTime) return null
  return ExerciseSegment(
    startTime = startTime,
    endTime = endTime,
    segmentType = healthConnectSegmentType(),
    repetitions = if (unit == WorkoutUnit.REPS) amount.coerceAtLeast(0) else 0,
  )
}

private fun WorkoutSet.healthConnectSegmentType(): Int = when {
  type == WorkoutExerciseType.PLANK -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
  type == WorkoutExerciseType.STEP_UP -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING
  exerciseId == "reverse-crunch" || exerciseName.contains("クランチ") || exerciseName.contains("腹筋") ->
    ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
  exerciseId == "lunge" || exerciseName.contains("ランジ") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
  else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
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
