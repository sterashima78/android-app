package dev.terashima.yomitorirss.feature.health

import java.time.Instant

enum class HealthExerciseSegmentType {
  CRUNCH,
  LUNGE,
  PLANK,
  STAIR_CLIMBING,
  OTHER,
}

data class HealthExerciseSegment(
  val startTime: Instant,
  val endTime: Instant,
  val type: HealthExerciseSegmentType,
  val repetitions: Int = 0,
)

data class HealthWorkoutSession(
  val clientRecordId: String,
  val startTime: Instant,
  val endTime: Instant,
  val title: String,
  val notes: String? = null,
  val segments: List<HealthExerciseSegment> = emptyList(),
)

enum class HealthWorkoutWriteResult {
  WRITTEN,
  PERMISSION_REQUIRED,
  UNAVAILABLE,
}

fun interface HealthWorkoutWriter {
  suspend fun writeWorkout(session: HealthWorkoutSession): HealthWorkoutWriteResult
}
