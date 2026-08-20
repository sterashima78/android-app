package dev.terashima.yomitorirss.feature.health.data

import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import java.time.Instant

internal fun deduplicateExerciseSessions(
  sessions: List<HealthExerciseSessionSummary>,
): List<HealthExerciseSessionSummary> =
  sessions
    .groupBy { session ->
      ExerciseSessionIdentity(
        startTime = session.startTime,
        endTime = session.endTime,
        exerciseName = session.exerciseName,
      )
    }
    .values
    .mapNotNull { duplicates ->
      duplicates.maxWithOrNull(
        compareBy<HealthExerciseSessionSummary> { it.segments.size }
          .thenBy { it.notes?.length ?: 0 }
          .thenBy { it.title?.length ?: 0 },
      )
    }

private data class ExerciseSessionIdentity(
  val startTime: Instant,
  val endTime: Instant,
  val exerciseName: String,
)
