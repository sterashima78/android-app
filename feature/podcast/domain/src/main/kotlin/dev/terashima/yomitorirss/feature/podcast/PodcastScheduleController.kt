package dev.terashima.yomitorirss.feature.podcast

interface PodcastScheduleController {
  /** Apply an explicit program setting change, including re-anchoring its next target time. */
  fun sync(program: PodcastProgram)

  /** Ensure persisted scheduling exists without replacing an already registered periodic job. */
  fun ensure(program: PodcastProgram)

  fun cancel(programId: String)
}
