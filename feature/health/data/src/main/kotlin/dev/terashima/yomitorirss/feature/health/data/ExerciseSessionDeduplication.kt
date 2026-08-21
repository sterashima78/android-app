package dev.terashima.yomitorirss.feature.health.data

import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import java.time.Duration

internal data class ExerciseSessionCandidate(
  val exerciseType: Int,
  val dataOriginPackageName: String,
  val summary: HealthExerciseSessionSummary,
)

internal fun deduplicateExerciseSessions(
  sessions: List<ExerciseSessionCandidate>,
): List<HealthExerciseSessionSummary> {
  if (sessions.size < 2) return sessions.map(ExerciseSessionCandidate::summary)

  val parents = IntArray(sessions.size) { it }

  fun find(index: Int): Int {
    var current = index
    while (parents[current] != current) {
      parents[current] = parents[parents[current]]
      current = parents[current]
    }
    return current
  }

  fun union(first: Int, second: Int) {
    val firstRoot = find(first)
    val secondRoot = find(second)
    if (firstRoot != secondRoot) parents[secondRoot] = firstRoot
  }

  sessions.indices.forEach { first ->
    for (second in first + 1 until sessions.size) {
      if (sameRealWorldExercise(sessions[first], sessions[second])) {
        union(first, second)
      }
    }
  }

  return sessions.indices
    .groupBy(::find)
    .values
    .mapNotNull { duplicateIndexes ->
      duplicateIndexes
        .map(sessions::get)
        .maxWithOrNull(EXERCISE_SESSION_PREFERENCE)
        ?.summary
    }
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

  if (first.dataOriginPackageName == second.dataOriginPackageName) return false
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
