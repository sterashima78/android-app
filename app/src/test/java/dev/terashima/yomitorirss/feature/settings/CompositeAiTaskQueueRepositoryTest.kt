package dev.terashima.yomitorirss.feature.settings

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookKey
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidate
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeAiTaskQueueRepositoryTest {
  @Test
  fun `AIタスク一覧に要約と蔵書整理を同じキューとして投影する`() = runBlocking {
    val summary = FakeSummaryRepository(
      tasks = listOf(
        SummaryQueueTask(
          articleId = "article-1",
          articleTitle = "Test article",
          sourceTitle = "Test source",
          state = SummaryQueueTaskState.QUEUED,
          queuedAt = "2026-08-16T00:00:00Z",
          startedAt = null,
          finishedAt = null,
          error = null,
        ),
      ),
    )
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.RUNNING))
    val repository = CompositeAiTaskQueueRepository(summary, library, FakeScheduler())

    val tasks = repository.listTasks()

    assertEquals(2, tasks.size)
    assertEquals(AiTaskQueueItemKind.LIBRARY_ORGANIZATION, tasks[0].kind)
    assertEquals(2, tasks[0].progressTotal)
    assertEquals(1, tasks[0].progressCurrent)
    assertEquals(1, tasks[0].pendingReviewCount)
    assertEquals(AiTaskQueueItemKind.SUMMARY, tasks[1].kind)
    assertEquals(AiTaskQueueItemState.QUEUED, tasks[1].state)
  }

  @Test
  fun `全体停止は実行中の蔵書整理を実行ゲートで止め再開時に再度キックする`() = runBlocking {
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.RUNNING))
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(summary, library, scheduler)

    repository.setPaused(true)

    assertTrue(summary.execution.paused)
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, library.batch!!.status)
    assertEquals(1, scheduler.cancelCount)
    assertTrue(scheduler.resumeOnChargingScheduled)
    assertEquals(AiTaskQueueItemState.PAUSED, repository.listTasks().first().state)

    repository.setPaused(false)

    assertFalse(summary.execution.paused)
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, library.batch!!.status)
    assertEquals(1, scheduler.kickCount)
    assertFalse(scheduler.resumeOnChargingScheduled)
  }

  @Test
  fun `個別に一時停止した蔵書整理は全体停止の解除や充電再開対象にしない`() = runBlocking {
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.PAUSED))
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(summary, library, scheduler)

    repository.setPaused(true)
    repository.setPaused(false)

    assertEquals(LibraryOrganizationBatchStatus.PAUSED, library.batch!!.status)
    assertEquals(0, scheduler.kickCount)
    assertFalse(scheduler.resumeOnChargingScheduled)
  }
}

private class FakeSummaryRepository(
  private val tasks: List<SummaryQueueTask> = emptyList(),
) : SummaryTaskQueueRepository {
  var execution = SummaryQueueExecutionState(paused = false, resumeWhenCharging = true)

  override suspend fun listTasks(): List<SummaryQueueTask> = tasks
  override suspend fun executionState(): SummaryQueueExecutionState = execution
  override suspend fun kick() = Unit
  override suspend fun setPaused(paused: Boolean) {
    execution = execution.copy(paused = paused)
  }
  override suspend fun setResumeWhenCharging(enabled: Boolean) {
    execution = execution.copy(resumeWhenCharging = enabled)
  }
  override suspend fun stop(articleId: String): Boolean = false
  override suspend fun cancel(articleId: String): Boolean = false
  override suspend fun resume(articleId: String): Boolean = false
}

private class FakeLibraryOrganizationRepository(
  var batch: LibraryOrganizationBatchSnapshot?,
) : LibraryOrganizationRepository {
  override suspend fun snapshot(): LibraryOrganizationSnapshot = LibraryOrganizationSnapshot()
  override suspend fun save(book: LibraryBook, draft: LibraryOrganizationDraft) = Unit
  override suspend fun batchSnapshot(): LibraryOrganizationBatchSnapshot? = batch
  override suspend fun startBatch(books: List<LibraryBook>): String = error("unused")
  override suspend fun pauseBatch() {
    batch = batch?.copy(status = LibraryOrganizationBatchStatus.PAUSED)
  }
  override suspend fun resumeBatch() {
    batch = batch?.copy(status = LibraryOrganizationBatchStatus.RUNNING)
  }
  override suspend fun updateCandidate(key: LibraryBookKey, draft: LibraryOrganizationDraft) = Unit
  override suspend fun acceptCandidate(book: LibraryBook, draft: LibraryOrganizationDraft) = Unit
  override suspend fun deferCandidate(key: LibraryBookKey) = Unit
  override suspend fun rejectCandidate(key: LibraryBookKey) = Unit
  override suspend fun reopenCandidate(key: LibraryBookKey) = Unit
  override suspend fun retryCandidate(key: LibraryBookKey) = Unit
}

private class FakeScheduler : LibraryOrganizationBatchScheduler {
  var kickCount = 0
  var cancelCount = 0
  var resumeOnChargingScheduled = false

  override fun kick() {
    kickCount += 1
  }

  override fun cancel() {
    cancelCount += 1
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    resumeOnChargingScheduled = enabled
  }
}

private fun testBatch(status: LibraryOrganizationBatchStatus): LibraryOrganizationBatchSnapshot =
  LibraryOrganizationBatchSnapshot(
    batchId = "batch-1",
    status = status,
    candidates = listOf(
      LibraryOrganizationCandidate(
        batchId = "batch-1",
        key = LibraryBookKey(LibrarySource.KINDLE, "book-1"),
        status = LibraryOrganizationCandidateStatus.PENDING_REVIEW,
        updatedAt = 1L,
      ),
      LibraryOrganizationCandidate(
        batchId = "batch-1",
        key = LibraryBookKey(LibrarySource.KINDLE, "book-2"),
        status = LibraryOrganizationCandidateStatus.QUEUED,
        updatedAt = 1L,
      ),
    ),
    createdAt = 1L,
    updatedAt = 1L,
  )
