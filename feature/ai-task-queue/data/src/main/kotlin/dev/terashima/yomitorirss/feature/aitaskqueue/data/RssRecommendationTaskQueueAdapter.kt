package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemPriority
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskReader
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskScheduler
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskState

internal class RssRecommendationTaskQueueAdapter(
  private val reader: RssRecommendationTaskReader,
  private val scheduler: RssRecommendationTaskScheduler,
) {
  fun tasks(globalPaused: Boolean): List<AiTaskQueueItem> = reader.listTasks().map { task ->
    AiTaskQueueItem(
      id = "$PREFIX${task.articleId}:${task.revision}",
      kind = AiTaskQueueItemKind.RSS_RECOMMENDATION,
      title = task.title,
      source = "未読記事",
      state = if (globalPaused) {
        AiTaskQueueItemState.PAUSED
      } else {
        when (task.state) {
          RssRecommendationTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
          RssRecommendationTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
        }
      },
      priority = AiTaskQueueItemPriority.NORMAL,
      executionProviderLabel = "ローカル",
    )
  }

  fun kick() {
    scheduler.kick()
  }

  suspend fun pauseForGlobalGate() {
    scheduler.pauseForGlobalGate()
  }

  fun resumeFromGlobalGate() {
    scheduler.kick()
  }

  fun setResumeOnChargingScheduled(enabled: Boolean, globalPaused: Boolean) {
    scheduler.setResumeOnChargingScheduled(enabled && globalPaused)
  }

  private companion object {
    const val PREFIX = "rss-recommendation:"
  }
}
