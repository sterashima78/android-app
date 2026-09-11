package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueProgressStage
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTask
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastTaskQueueAdapterTest {
  @Test
  fun `Podcast episodeをチャプター進捗付きAIタスクへ投影する`() = runBlocking {
    val adapter = PodcastTaskQueueAdapter(
      reader = object : PodcastGenerationTaskReader {
        override suspend fun listGenerationTasks(): List<PodcastGenerationTask> = listOf(
          PodcastGenerationTask(
            episodeId = "episode-1",
            title = "朝のニュース",
            programName = "朝のニュース",
            provider = PodcastGenerationProvider.CLOUD,
            state = PodcastGenerationTaskState.RUNNING,
            completedChapters = 17,
            totalChapters = 50,
            error = null,
            createdAtEpochMillis = 100L,
          ),
        )
      },
    )

    val item = adapter.tasks().single()

    assertEquals("podcast:episode-1", item.id)
    assertEquals(AiTaskQueueItemKind.PODCAST_EPISODE, item.kind)
    assertEquals(AiTaskQueueItemState.RUNNING, item.state)
    assertEquals(AiTaskQueueProgressStage.GENERATING_CHAPTER, item.progressStage)
    assertEquals(17, item.progressCurrent)
    assertEquals(50, item.progressTotal)
    assertEquals("クラウド", item.executionProviderLabel)
  }
}
