package dev.terashima.yomitorirss.feature.podcast

interface PodcastGenerationController {
  /** Enqueue a new episode generation request without tying execution to UI lifetime. */
  fun generate(programId: String)

  /** Rebuild an existing episode using its saved article snapshot and current program settings. */
  fun regenerate(programId: String, episodeId: String)
}
