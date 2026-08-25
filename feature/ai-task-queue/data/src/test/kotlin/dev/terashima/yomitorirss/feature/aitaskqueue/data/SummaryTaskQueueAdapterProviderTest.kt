package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTaskQueueAdapterProviderTest {
  @Test
  fun `summaryの実行先を統合AIタスクへ引き継ぐ`() = runBlocking {
    val repository = FakeSummaryQueueRepository(
      SummaryQueueTask(
        articleId = "article-1",
        articleTitle = "記事",
        sourceTitle = "source",
        state = SummaryQueueTaskState.QUEUED,
        queuedAt = "2026-08-25T00:00:00Z",
        startedAt = null,
        finishedAt = null,
        error = null,
        executionProvider = SummaryExecutionProvider.CHATGPT,
      ),
    )

    val item = SummaryTaskQueueAdapter(repository).tasks().single()

    assertEquals(AiTaskExecutionProvider.CHATGPT, item.executionProvider)
  }
}

private class FakeSummaryQueueRepository(
  private val task: SummaryQueueTask,
) : SummaryTaskQueueRepository {
  override suspend fun listTasks(): List<SummaryQueueTask> = listOf(task)
  override suspend fun executionState() = SummaryQueueExecutionState(false, false, false)
  override suspend fun kick() = Unit
  override suspend fun setLocalPaused(paused: Boolean) = Unit
  override suspend fun setCloudPaused(paused: Boolean) = Unit
  override suspend fun setResumeLocalWhenCharging(enabled: Boolean) = Unit
  override suspend fun stop(articleId: String) = false
  override suspend fun cancel(articleId: String) = false
  override suspend fun resume(articleId: String) = false
}
