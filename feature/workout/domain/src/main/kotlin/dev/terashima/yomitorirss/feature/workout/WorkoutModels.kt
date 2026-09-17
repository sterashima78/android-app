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
  val targetSets: Int = 3,
  val unit: WorkoutUnit,
  val type: WorkoutExerciseType,
)

enum class WorkoutMenuSource {
  PRESET,
  GENERATED,
  IMPORTED,
}

data class WorkoutMenuItem(
  val exerciseId: String,
  val targetSets: Int,
  val targets: List<Int> = emptyList(),
) {
  init {
    require(targetSets > 0) { "targetSets must be positive" }
    require(targets.all { it > 0 }) { "targets must be positive" }
    require(targets.isEmpty() || targets.size == targetSets) {
      "targets must be empty or contain one value per set"
    }
  }
}

data class WorkoutMenu(
  val id: String,
  val name: String,
  val items: List<WorkoutMenuItem>,
  val source: WorkoutMenuSource = WorkoutMenuSource.PRESET,
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
  val menu: WorkoutMenu? = null,
)

data class WorkoutHistory(
  val id: String,
  val date: String,
  val startedAt: String?,
  val finishedAt: String,
  val sets: List<WorkoutSet>,
)

data class WorkoutSnapshot(
  val version: Int = 2,
  val exercises: List<WorkoutExercise>,
  val menus: List<WorkoutMenu> = emptyList(),
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

fun defaultWorkoutMenu(exercises: List<WorkoutExercise>): WorkoutMenu = WorkoutMenu(
  id = "default",
  name = "基本メニュー",
  items = exercises.map { exercise ->
    WorkoutMenuItem(
      exerciseId = exercise.id,
      targetSets = exercise.targetSets.coerceAtLeast(1),
    )
  },
)

fun newWorkoutSnapshot(date: String): WorkoutSnapshot {
  val exercises = defaultWorkoutExercises()
  return WorkoutSnapshot(
    exercises = exercises,
    menus = listOf(defaultWorkoutMenu(exercises)),
    today = WorkoutDay(date = date),
  )
}

fun WorkoutSnapshot.effectiveMenu(): WorkoutMenu =
  today.menu ?: menus.firstOrNull() ?: defaultWorkoutMenu(exercises)

fun WorkoutSnapshot.menuExercises(): List<WorkoutExercise> {
  val byId = exercises.associateBy { it.id }
  return effectiveMenu().items.mapNotNull { item ->
    byId[item.exerciseId]?.copy(targetSets = item.targetSets)
  }
}

fun WorkoutSnapshot.menuItem(exerciseId: String): WorkoutMenuItem? =
  effectiveMenu().items.firstOrNull { it.exerciseId == exerciseId }

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
