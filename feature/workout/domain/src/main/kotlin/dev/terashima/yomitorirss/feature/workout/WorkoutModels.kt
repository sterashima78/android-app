package dev.terashima.yomitorirss.feature.workout

enum class WorkoutExerciseType {
  REPS,
  TIMED,
  PLANK,
  STEP_UP,
}

enum class WorkoutUnit(val label: String) {
  REPS("回"),
  SECONDS("秒"),
}

data class WorkoutExercise(
  val id: String,
  val name: String,
  val targetSets: Int,
  val unit: WorkoutUnit,
  val type: WorkoutExerciseType,
)

data class WorkoutSet(
  val id: String,
  val exerciseId: String,
  val exerciseName: String,
  val unit: WorkoutUnit,
  val type: WorkoutExerciseType,
  val amount: Int,
  val steps: Int? = null,
  val memo: String = "",
  val recordedAt: String,
  val startedAt: String? = null,
  val finishedAt: String? = null,
)

data class WorkoutDay(
  val date: String,
  val startedAt: String? = null,
  val sets: List<WorkoutSet> = emptyList(),
)

data class WorkoutHistory(
  val id: String,
  val date: String,
  val startedAt: String?,
  val finishedAt: String,
  val sets: List<WorkoutSet>,
)

data class WorkoutSnapshot(
  val version: Int = 1,
  val exercises: List<WorkoutExercise>,
  val today: WorkoutDay,
  val history: List<WorkoutHistory> = emptyList(),
  val lastAmounts: Map<String, Int> = emptyMap(),
  val lastStepCounts: Map<String, Int> = emptyMap(),
)

fun defaultWorkoutExercises(): List<WorkoutExercise> = listOf(
  WorkoutExercise("push-up", "腕立て伏せ", 3, WorkoutUnit.REPS, WorkoutExerciseType.REPS),
  WorkoutExercise("reverse-crunch", "リバースクランチ", 3, WorkoutUnit.REPS, WorkoutExerciseType.REPS),
  WorkoutExercise("lunge", "ランジ", 3, WorkoutUnit.REPS, WorkoutExerciseType.REPS),
  WorkoutExercise("step-up", "踏み台昇降", 1, WorkoutUnit.SECONDS, WorkoutExerciseType.STEP_UP),
  WorkoutExercise("plank", "プランク", 3, WorkoutUnit.SECONDS, WorkoutExerciseType.PLANK),
)

fun newWorkoutSnapshot(date: String): WorkoutSnapshot = WorkoutSnapshot(
  exercises = defaultWorkoutExercises(),
  today = WorkoutDay(date = date),
)

fun WorkoutSnapshot.rolloverTo(date: String, finishedAt: String): WorkoutSnapshot {
  if (today.date.isBlank() || today.date == date) {
    return if (today.date == date) this else copy(today = today.copy(date = date))
  }
  val nextHistory = if (today.sets.isEmpty()) {
    history
  } else {
    listOf(
      WorkoutHistory(
        id = "${today.date}-$finishedAt",
        date = today.date,
        startedAt = today.startedAt,
        finishedAt = finishedAt,
        sets = today.sets,
      ),
    ) + history
  }
  return copy(
    today = WorkoutDay(date = date),
    history = nextHistory.take(50),
  )
}

fun inferWorkoutExerciseType(name: String, unit: WorkoutUnit): WorkoutExerciseType = when {
  name.contains("プランク") -> WorkoutExerciseType.PLANK
  name.contains("踏み台") || name.contains("昇降") -> WorkoutExerciseType.STEP_UP
  unit == WorkoutUnit.SECONDS -> WorkoutExerciseType.TIMED
  else -> WorkoutExerciseType.REPS
}
