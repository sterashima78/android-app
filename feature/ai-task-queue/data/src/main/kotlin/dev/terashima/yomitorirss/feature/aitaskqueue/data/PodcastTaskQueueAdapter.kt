package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItem
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueProgressStage
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTask
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState

internal class PodcastTaskQueueAdapter(
  private val reader: PodcastGenerationTaskReader,
) {
  suspend fun tasks(): List<AiTaskQueueItem> = reader.listGenerationTasks().map(::toAiTaskQueueItem)

  private fun toAiTaskQueueItem(task: PodcastGenerationTask): AiTaskQueueItem {
    val remainingChapters = (task.totalChapters - task.completedChapters).coerceAtLeast(0)
    return AiTaskQueueItem(
      id = "$PREFIX${task.episodeId}",
      kind = AiTaskQueueItemKind.PODCAST_EPISODE,
      title = task.title,
      source = "${task.totalChapters}記事・未完了 ${remainingChapters}記事",
      state = task.state.toAiTaskState(),
      progressStage = if (task.state == PodcastGenerationTaskState.RUNNING) {
        AiTaskQueueProgressStage.GENERATING_CHAPTER
      } else {
        null
      },
      progressCurrent = task.completedChapters,
      progressTotal = task.totalChapters,
      error = task.error,
      executionProviderLabel = task.provider.displayLabel(),
    )
  }

  private fun PodcastGenerationTaskState.toAiTaskState(): AiTaskQueueItemState = when (this) {
    PodcastGenerationTaskState.QUEUED -> AiTaskQueueItemState.QUEUED
    PodcastGenerationTaskState.RUNNING -> AiTaskQueueItemState.RUNNING
    PodcastGenerationTaskState.FAILED -> AiTaskQueueItemState.FAILED
  }

  private fun PodcastGenerationProvider.displayLabel(): String = when (this) {
    PodcastGenerationProvider.LOCAL -> "ローカル"
    PodcastGenerationProvider.CLOUD -> "クラウド"
  }

  private companion object {
    const val PREFIX = "podcast:"
  }
}
