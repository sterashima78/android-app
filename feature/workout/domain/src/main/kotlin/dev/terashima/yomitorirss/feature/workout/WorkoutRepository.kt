package dev.terashima.yomitorirss.feature.workout

interface WorkoutRepository {
  suspend fun load(): WorkoutSnapshot
  suspend fun save(snapshot: WorkoutSnapshot)
}
