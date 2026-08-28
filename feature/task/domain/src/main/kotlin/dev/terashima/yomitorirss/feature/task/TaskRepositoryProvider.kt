package dev.terashima.yomitorirss.feature.task

/** Application-level access contract used by framework-owned task integrations. */
interface TaskRepositoryProvider {
  val taskRepository: TaskRepository
}
