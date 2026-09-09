package dev.terashima.yomitorirss.feature.podcast

interface PodcastScheduleController {
  /** Apply an explicit program setting change, including re-anchoring its next target time. */
  fun sync(program: PodcastProgram)

  /** Ensure persisted scheduling exists without replacing an already registered job. */
  fun ensure(program: PodcastProgram)

  /** Append the next local wall-clock occurrence after the currently running job. */
  fun scheduleNext(program: PodcastProgram)

  fun cancel(programId: String)
}
