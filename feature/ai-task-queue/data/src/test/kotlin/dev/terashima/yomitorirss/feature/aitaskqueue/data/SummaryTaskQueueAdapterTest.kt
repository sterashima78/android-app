package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemPriority
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskPriority
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTaskQueueAdapterTest {
  @Test
  fun `summary taskの状態と優先度と進捗をAI taskへ投影する`() = runBlocking {
    val repository = FakeSummaryQueueRepository(
      task(
        state = SummaryQueueTaskState.RUNNING,
        priority = SummaryQueueTaskPriority.HIGH,
        progressStage = SummaryQueueTaskProgressStage.SUMMARIZING_CHUNK,
        provider = SummaryExecutionProvider.CHATGPT,
      ),
    )

    val item = SummaryTaskQueueAdapter(repository).tasks().single()

    assertEquals("summary:article-1", item.id)
    assertEquals(AiTaskQueueItemState.RUNNING, item.state)
    assertEquals(AiTaskQueueItemPriority.HIGH, item.priority)
    assertEquals(AiTaskQueueProgressStage.PROCESSING_CHUNK, item.progressStage)
    assertTrue(item.canStop)
    assertTrue(item.canCancel)
    assertFalse(item.canResume)
    assertEquals("ChatGPT", item.executionProviderLabel)
  }

  @Test
  fun `stopped taskはresume可能でstop不可になる`() = runBlocking {
    val item = SummaryTaskQueueAdapter(
      FakeSummaryQueueRepository(task(state = SummaryQueueTaskState.STOPPED)),
    ).tasks().single()

    assertEquals(AiTaskQueueItemState.STOPPED, item.state)
    assertFalse(item.canStop)
    assertTrue(item.canCancel)
    assertTrue(item.canResume)
  }

  @Test
  fun `summary prefixのtaskだけ操作をrepositoryへ委譲する`() = runBlocking {
    val repository = FakeSummaryQueueRepository(task())
    val adapter = SummaryTaskQueueAdapter(repository)

    assertTrue(adapter.resume("summary:article-1") == true)
    assertEquals("article-1", repository.resumedArticleId)
    assertNull(adapter.resume("knowledge:article-1"))
  }

  private fun task(
    state: SummaryQueueTaskState = SummaryQueueTaskState.QUEUED,
    priority: SummaryQueueTaskPriority = SummaryQueueTaskPriority.NORMAL,
    progressStage: SummaryQueueTaskProgressStage? = null,
    provider: SummaryExecutionProvider = SummaryExecutionProvider.LOCAL,
  ) = SummaryQueueTask(
    articleId = "article-1",
    articleTitle = "記事",
    sourceTitle = "source",
    state = state,
    queuedAt = "2026-08-25T00:00:00Z",
    startedAt = null,
    finishedAt = null,
    error = null,
    priority = priority,
    progressStage = progressStage,
    progressCurrent = 1,
    progressTotal = 3,
    executionProvider = provider,
  )
}

private class FakeSummaryQueueRepository(
  private val task: SummaryQueueTask,
) : SummaryTaskQueueRepository {
  var resumedArticleId: String? = null

  override suspend fun listTasks(): List<SummaryQueueTask> = listOf(task)
  override suspend fun executionState() = SummaryQueueExecutionState(false, false, false)
  override suspend fun kick() = Unit
  override suspend fun setLocalPaused(paused: Boolean) = Unit
  override suspend fun setCloudPaused(paused: Boolean) = Unit
  override suspend fun setResumeLocalWhenCharging(enabled: Boolean) = Unit
  override suspend fun stop(articleId: String) = true
  override suspend fun cancel(articleId: String) = true
  override suspend fun resume(articleId: String): Boolean {
    resumedArticleId = articleId
    return true
  }
}
