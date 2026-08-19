package dev.terashima.yomitorirss.feature.workout

internal fun formatWorkoutHistoryExercise(sets: List<WorkoutSet>): String {
  require(sets.isNotEmpty()) { "Workout history exercise sets must not be empty" }
  val first = sets.first()
  val total = sets.sumOf { it.amount }
  val steps = sets.sumOf { it.steps ?: 0 }
  return buildString {
    append("${first.exerciseName}: ${sets.size}セット / ")
    append(if (first.unit == WorkoutUnit.SECONDS) formatHistoryDuration(total) else "$total${first.unit.label}")
    if (steps > 0) append(" / ${steps}段")
  }
}

internal fun formatWorkoutHistoryForCopy(history: WorkoutHistory): String {
  val exerciseLines = history.sets
    .groupBy { it.exerciseId }
    .values
    .map(::formatWorkoutHistoryExercise)
  return (listOf(history.date, "${history.sets.size} セット") + exerciseLines).joinToString("\n")
}

private fun formatHistoryDuration(totalSeconds: Int): String {
  val seconds = totalSeconds.coerceAtLeast(0)
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val rest = seconds % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
}
