package dev.terashima.yomitorirss.feature.health.data

import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import java.time.Duration

internal data class ExerciseSessionCandidate(
  val exerciseType: Int,
  val summary: HealthExerciseSessionSummary,
  val dataOriginPackageName: String = "",
)

internal fun deduplicateExerciseSessions(
  sessions: List<ExerciseSessionCandidate>,
): List<HealthExerciseSessionSummary> {
  val kept = mutableListOf<ExerciseSessionCandidate>()
  sessions
    .sortedWith(EXERCISE_SESSION_PREFERENCE.reversed())
    .forEach { candidate ->
      if (kept.none { representative -> sameRealWorldExercise(representative, candidate) }) {
        kept += candidate
      }
    }
  return kept.map(ExerciseSessionCandidate::summary)
}

private fun sameRealWorldExercise(
  first: ExerciseSessionCandidate,
  second: ExerciseSessionCandidate,
): Boolean {
  val exactIdentity =
    first.exerciseType == second.exerciseType &&
      first.summary.startTime == second.summary.startTime &&
      first.summary.endTime == second.summary.endTime
  if (exactIdentity) return true

  val sameKnownOrigin =
    first.dataOriginPackageName.isNotBlank() &&
      first.dataOriginPackageName == second.dataOriginPackageName
  if (sameKnownOrigin) return false

  return hasStrongTemporalOverlap(first.summary, second.summary)
}

private fun hasStrongTemporalOverlap(
  first: HealthExerciseSessionSummary,
  second: HealthExerciseSessionSummary,
): Boolean {
  val firstDuration = durationMillis(first)
  val secondDuration = durationMillis(second)
  if (firstDuration <= 0L || secondDuration <= 0L) return false

  val overlapStart = maxOf(first.startTime, second.startTime)
  val overlapEnd = minOf(first.endTime, second.endTime)
  if (overlapStart >= overlapEnd) return false

  val overlapDuration = Duration.between(overlapStart, overlapEnd).toMillis().toDouble()
  val shorterDuration = minOf(firstDuration, secondDuration).toDouble()
  val longerDuration = maxOf(firstDuration, secondDuration).toDouble()

  return overlapDuration / shorterDuration >= MIN_OVERLAP_RATIO &&
    shorterDuration / longerDuration >= MIN_DURATION_RATIO
}

private fun durationMillis(summary: HealthExerciseSessionSummary): Long =
  Duration.between(summary.startTime, summary.endTime).toMillis()

private val EXERCISE_SESSION_PREFERENCE =
  compareBy<ExerciseSessionCandidate> { it.summary.segments.size }
    .thenBy { it.summary.notes?.length ?: 0 }
    .thenBy { it.summary.title?.length ?: 0 }
    .thenBy { durationMillis(it.summary) }

private const val MIN_OVERLAP_RATIO = 0.80
private const val MIN_DURATION_RATIO = 0.60
