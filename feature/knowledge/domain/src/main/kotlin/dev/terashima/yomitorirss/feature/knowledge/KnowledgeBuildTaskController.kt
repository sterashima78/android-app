package dev.terashima.yomitorirss.feature.knowledge

enum class KnowledgeBuildTaskState {
  QUEUED,
  RUNNING,
  PAUSED,
  STOPPED,
  FAILED,
}

data class KnowledgeBuildTaskSnapshot(
  val state: KnowledgeBuildTaskState,
  val error: String? = null,
)

interface KnowledgeBuildTaskController {
  fun kick()
  suspend fun pauseForGlobalGate()
  suspend fun stop(): Boolean
  suspend fun cancel(): Boolean
  suspend fun resume(): Boolean
  suspend fun snapshot(): KnowledgeBuildTaskSnapshot?
  fun setResumeOnChargingScheduled(enabled: Boolean)
}
