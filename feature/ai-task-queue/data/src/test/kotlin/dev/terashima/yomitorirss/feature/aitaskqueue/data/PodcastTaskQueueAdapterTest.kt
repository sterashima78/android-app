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
    assertEquals("50記事・未完了 33記事", item.source)
    assertEquals(17, item.progressCurrent)
    assertEquals(50, item.progressTotal)
    assertEquals("クラウド", item.executionProviderLabel)
  }

  @Test
  fun `中断したPodcast episodeは未完了記事数を保持してAIタスクへ投影する`() = runBlocking {
    val adapter = PodcastTaskQueueAdapter(
      reader = object : PodcastGenerationTaskReader {
        override suspend fun listGenerationTasks(): List<PodcastGenerationTask> = listOf(
          PodcastGenerationTask(
            episodeId = "episode-interrupted",
            title = "夕方のニュース",
            programName = "夕方のニュース",
            provider = PodcastGenerationProvider.LOCAL,
            state = PodcastGenerationTaskState.FAILED,
            completedChapters = 3,
            totalChapters = 8,
            error = "generation interrupted",
            createdAtEpochMillis = 200L,
          ),
        )
      },
    )

    val item = adapter.tasks().single()

    assertEquals(AiTaskQueueItemState.FAILED, item.state)
    assertEquals("8記事・未完了 5記事", item.source)
    assertEquals(3, item.progressCurrent)
    assertEquals(8, item.progressTotal)
    assertEquals("ローカル", item.executionProviderLabel)
  }
}
