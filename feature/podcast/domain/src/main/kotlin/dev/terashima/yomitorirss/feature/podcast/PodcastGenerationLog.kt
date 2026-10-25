package dev.terashima.yomitorirss.feature.podcast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Bounded, diagnostic-only events; never store article text, URLs or raw exception messages. */
enum class PodcastGenerationLogLevel { INFO, WARNING, ERROR }

data class PodcastGenerationLog(
  val programId: String,
  val recordedAtEpochMillis: Long,
  val level: PodcastGenerationLogLevel,
  val message: String,
)

interface PodcastGenerationLogStore {
  val logs: StateFlow<List<PodcastGenerationLog>>
  fun record(log: PodcastGenerationLog)
  fun clear(programId: String)
}

object NoOpPodcastGenerationLogStore : PodcastGenerationLogStore {
  override val logs: StateFlow<List<PodcastGenerationLog>> = MutableStateFlow(emptyList())
  override fun record(log: PodcastGenerationLog) = Unit
  override fun clear(programId: String) = Unit
}
