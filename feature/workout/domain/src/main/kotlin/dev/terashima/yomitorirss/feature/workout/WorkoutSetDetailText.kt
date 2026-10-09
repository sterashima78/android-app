package dev.terashima.yomitorirss.feature.workout

/**
 * Explicitly recorded first-party set information. Empty output means no extra data was provided.
 * Never derive rest from timer settings or approximate timestamps.
 */
fun formatWorkoutSetDetails(set: WorkoutSet): String = buildList {
  set.rpe?.let { add("RPE $it/10") }
  set.formQuality?.let { add("フォーム: ${it.label}") }
  set.load?.let { load ->
    val value = when (load.kind) {
      WorkoutLoadKind.BODY_ANGLE -> "身体の角度: ${load.value}"
      WorkoutLoadKind.ADDED_WEIGHT -> "追加重量: ${load.value}kg"
      WorkoutLoadKind.ASSISTED_WEIGHT -> "補助重量: ${load.value}kg"
      WorkoutLoadKind.OTHER -> "負荷条件: ${load.value}"
    }
    add(value)
  }
  set.restSeconds?.let { add("直前の休憩: ${it}秒") }
}.joinToString(" / ")
