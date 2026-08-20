package dev.terashima.yomitorirss.feature.health.data

import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import java.time.Instant

internal data class ExerciseSessionCandidate(
  val exerciseType: Int,
  val summary: HealthExerciseSessionSummary,
)

internal fun deduplicateExerciseSessions(
  sessions: List<ExerciseSessionCandidate>,
): List<HealthExerciseSessionSummary> =
  sessions
    .groupBy { candidate ->
      ExerciseSessionIdentity(
        startTime = candidate.summary.startTime,
        endTime = candidate.summary.endTime,
        exerciseType = candidate.exerciseType,
      )
    }
    .values
    .mapNotNull { duplicates ->
      duplicates.maxWithOrNull(
        compareBy<ExerciseSessionCandidate> { it.summary.segments.size }
          .thenBy { it.summary.notes?.length ?: 0 }
          .thenBy { it.summary.title?.length ?: 0 },
      )?.summary
    }

private data class ExerciseSessionIdentity(
  val startTime: Instant,
  val endTime: Instant,
  val exerciseType: Int,
)
