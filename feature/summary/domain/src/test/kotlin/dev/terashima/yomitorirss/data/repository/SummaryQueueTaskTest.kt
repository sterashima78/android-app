package dev.terashima.yomitorirss.feature.summary
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryQueueTaskTest {
  @Test
  fun `失敗状態とエラー内容を保持する`() {
    val task = SummaryQueueTask(
      articleId = "article-1",
      articleTitle = "Article",
      sourceTitle = "Source",
      state = SummaryQueueTaskState.FAILED,
      queuedAt = "2026-08-08T00:00:00Z",
      startedAt = "2026-08-08T00:01:00Z",
      finishedAt = "2026-08-08T00:02:00Z",
      error = "failed",
    )

    assertEquals(SummaryQueueTaskState.FAILED, task.state)
    assertEquals("failed", task.error)
  }
}
