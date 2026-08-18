package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskSnapshot
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState

internal class KnowledgeTaskQueueAdapter(
  private val controller: KnowledgeBuildTaskController,
) {
  suspend fun tasks(): List<AiTaskQueueItem> = listOfNotNull(controller.snapshot()?.let(::toAiTaskQueueItem))

  fun kick() = controller.kick()

  suspend fun pauseForGlobalGate() = controller.pauseForGlobalGate()

  fun setResumeOnChargingScheduled(enabled: Boolean) = controller.setResumeOnChargingScheduled(enabled)

  suspend fun stop(taskId: String): Boolean? =
    if (taskId == TASK_ID) controller.stop() else null

  suspend fun cancel(taskId: String): Boolean? =
    if (taskId == TASK_ID) controller.cancel() else null

  suspend fun resume(taskId: String): Boolean? =
    if (taskId == TASK_ID) controller.resume() else null

  private fun toAiTaskQueueItem(task: KnowledgeBuildTaskSnapshot): AiTaskQueueItem {
    val state = task.state.toAiTaskState()
    return AiTaskQueueItem(
      id = TASK_ID,
      kind = AiTaskQueueItemKind.KNOWLEDGE_WIKI,
      title = "自動Wikiを構築",
      source = "保存済み要約",
      state = state,
      error = task.error,
      canStop = state == AiTaskQueueItemState.QUEUED || state == AiTaskQueueItemState.RUNNING,
      canCancel = state == AiTaskQueueItemState.QUEUED ||
        state == AiTaskQueueItemState.RUNNING ||
        state == AiTaskQueueItemState.PAUSED ||
        state == AiTaskQueueItemState.STOPPED ||
        state == AiTaskQueueItemState.FAILED,
      canResume = state == AiTaskQueueItemState.STOPPED || state == AiTaskQueueItemState.FAILED,
    )
  }

  private fun KnowledgeBuildTaskState.toAiTaskState(): AiTaskQueueItemState = when (this) {
    KnowledgeBuildTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
    KnowledgeBuildTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
    KnowledgeBuildTaskState.PAUSED -> AiTaskQueueItemState.PAUSED
    KnowledgeBuildTaskState.STOPPED -> AiTaskQueueItemState.STOPPED
    KnowledgeBuildTaskState.FAILED -> AiTaskQueueItemState.FAILED
  }

  private companion object {
    const val TASK_ID = "knowledge:auto-wiki"
  }
}
