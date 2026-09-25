package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTask
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskReader
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskScheduler
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRecommendationTaskQueueAdapterTest {
  @Test
  fun `RSS推薦taskを記事単位で共通キューへ投影する`() {
    val scheduler = RecordingScheduler()
    val adapter = RssRecommendationTaskQueueAdapter(
      reader = FakeReader(
        listOf(
          task("a1", RssRecommendationTaskState.RUNNING),
          task("a2", RssRecommendationTaskState.QUEUED),
        ),
      ),
      scheduler = scheduler,
    )

    val tasks = adapter.tasks(globalPaused = false)

    assertEquals(2, tasks.size)
    assertEquals(AiTaskQueueItemKind.RSS_RECOMMENDATION, tasks[0].kind)
    assertEquals(AiTaskQueueItemState.RUNNING, tasks[0].state)
    assertEquals(AiTaskQueueItemState.QUEUED, tasks[1].state)
  }

  @Test
  fun `global pause中はRSS推薦taskを一時停止として投影する`() {
    val adapter = RssRecommendationTaskQueueAdapter(
      reader = FakeReader(listOf(task("a1", RssRecommendationTaskState.RUNNING))),
      scheduler = RecordingScheduler(),
    )

    val item = adapter.tasks(globalPaused = true).single()

    assertEquals(AiTaskQueueItemState.PAUSED, item.state)
  }

  @Test
  fun `共通実行制御をRSS schedulerへ委譲する`() = runBlocking {
    val scheduler = RecordingScheduler()
    val adapter = RssRecommendationTaskQueueAdapter(
      reader = FakeReader(emptyList()),
      scheduler = scheduler,
    )

    adapter.kick()
    adapter.pauseForGlobalGate()
    adapter.resumeFromGlobalGate()
    adapter.setResumeOnChargingScheduled(enabled = true, globalPaused = true)

    assertEquals(2, scheduler.kickCount)
    assertTrue(scheduler.paused)
    assertTrue(scheduler.resumeOnChargingEnabled)

    adapter.setResumeOnChargingScheduled(enabled = true, globalPaused = false)
    assertFalse(scheduler.resumeOnChargingEnabled)
    assertEquals(listOf(false, true, false), scheduler.resumeOnChargingChanges)
  }

  private fun task(id: String, state: RssRecommendationTaskState) = RssRecommendationTask(
    articleId = id,
    title = "記事$id",
    revision = 2L,
    state = state,
    queuedAt = 1L,
    startedAt = if (state == RssRecommendationTaskState.RUNNING) 2L else null,
  )
}

private class FakeReader(
  private val tasks: List<RssRecommendationTask>,
) : RssRecommendationTaskReader {
  override fun listTasks(): List<RssRecommendationTask> = tasks
}

private class RecordingScheduler : RssRecommendationTaskScheduler {
  var kickCount = 0
  var paused = false
  var resumeOnChargingEnabled = false
  val resumeOnChargingChanges = mutableListOf<Boolean>()

  override suspend fun enqueueForFeed(feedId: String) = Unit
  override suspend fun enqueueUnread() = Unit

  override fun kick() {
    kickCount += 1
  }

  override suspend fun pauseForGlobalGate() {
    paused = true
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    resumeOnChargingEnabled = enabled
    resumeOnChargingChanges += enabled
  }
}
