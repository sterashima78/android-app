package dev.terashima.yomitorirss.feature.workout

interface WorkoutReader {
  suspend fun load(): WorkoutSnapshot
}

interface WorkoutRepository : WorkoutReader {
  suspend fun save(snapshot: WorkoutSnapshot)
}
