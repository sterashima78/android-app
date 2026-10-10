package dev.terashima.yomitorirss.feature.workout

enum class WorkoutTab(val label: String) {
  WORKOUT("記録"),
  TIMER("タイマー"),
  HISTORY("履歴"),
  CHAT("チャット"),
  SETTINGS("設定"),
}

data class WorkoutUiState(
  val initialized: Boolean = false,
  val loadError: String? = null,
  val snapshot: WorkoutSnapshot = newWorkoutSnapshot(""),
  val selectedExerciseId: String = "",
  val selectedTab: WorkoutTab = WorkoutTab.WORKOUT,
  val amount: String = "10",
  val memo: String = "",
  val stepCount: String = "",
  val rpe: Int? = null,
  val formQuality: WorkoutFormQuality? = null,
  val loadKind: WorkoutLoadKind? = null,
  val loadValue: String = "",
  val restSeconds: String = "",
  val intervalDurationSeconds: Int = 90,
  val intervalRemainingSeconds: Int = 90,
  val intervalRunning: Boolean = false,
  val intervalCompletionToken: Int = 0,
  val plankSeconds: Int = 0,
  val plankRunning: Boolean = false,
  val stepUpSeconds: Int = 0,
  val stepUpRunning: Boolean = false,
  val exportMessage: String? = null,
  val exportPermissionRequired: Boolean = false,
  val menuMessage: String? = null,
) {
  val detailsValid: Boolean
    get() = (loadKind?.let { kind -> runCatching { WorkoutLoad(kind, loadValue) }.isSuccess } ?: true) &&
      (restSeconds.isBlank() || restSeconds.toIntOrNull()?.let { it >= 0 } == true)

  fun selectedLoad(): WorkoutLoad? = loadKind?.let { kind ->
    runCatching { WorkoutLoad(kind, loadValue.trim()) }.getOrNull()
  }

  val activeMenu: WorkoutMenu
    get() = snapshot.effectiveMenu()

  val menuExercises: List<WorkoutExercise>
    get() = snapshot.menuExercises()

  val activeExercise: WorkoutExercise?
    get() = menuExercises.firstOrNull { it.id == selectedExerciseId } ?: menuExercises.firstOrNull()

  val activeSets: List<WorkoutSet>
    get() = activeExercise?.let { exercise -> snapshot.today.sets.filter { it.exerciseId == exercise.id } }.orEmpty()

  val nextTarget: Int?
    get() = activeExercise?.let { exercise ->
      snapshot.menuItem(exercise.id)?.targets?.getOrNull(activeSets.size)
    }
}
