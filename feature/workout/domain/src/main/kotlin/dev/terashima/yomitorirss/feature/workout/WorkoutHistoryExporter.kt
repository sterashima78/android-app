package dev.terashima.yomitorirss.feature.workout

enum class WorkoutExportResult {
  EXPORTED,
  PERMISSION_REQUIRED,
  UNAVAILABLE,
  FAILED,
}

fun interface WorkoutHistoryExporter {
  suspend fun export(history: WorkoutHistory): WorkoutExportResult
}
