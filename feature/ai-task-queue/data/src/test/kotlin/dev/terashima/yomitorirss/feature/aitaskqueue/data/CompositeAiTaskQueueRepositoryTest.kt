package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemKind
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueProgressStage
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
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.summary.SummaryQueueExecutionState
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTask
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskProgressStage
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskState
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeAiTaskQueueRepositoryTest {
  @Test
  fun `AIタスク一覧に要約と蔵書整理を一冊ずつ同じキューとして投影する`() = runBlocking {
    val summary = FakeSummaryRepository(
      tasks = listOf(
        SummaryQueueTask(
          articleId = "article-1",
          articleTitle = "Test article",
          sourceTitle = "Test source",
          state = SummaryQueueTaskState.RUNNING,
          queuedAt = "2026-08-16T00:00:00Z",
          startedAt = "2026-08-16T00:00:01Z",
          finishedAt = null,
          error = null,
          progressStage = SummaryQueueTaskProgressStage.CLOUD_GENERATING_SUMMARY,
        ),
      ),
    )
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.RUNNING))
    val repository = CompositeAiTaskQueueRepository(
      summaryRepository = summary,
      libraryRepository = library,
      libraryCatalogRepository = FakeLibraryCatalogRepository(testBooks()),
      libraryScheduler = FakeScheduler(),
    )

    val tasks = repository.listTasks()

    assertEquals(3, tasks.size)
    assertEquals(AiTaskQueueItemKind.LIBRARY_ORGANIZATION, tasks[0].kind)
    assertEquals("Book one", tasks[0].title)
    assertEquals("Kindle", tasks[0].source)
    assertEquals(AiTaskQueueItemState.COMPLETED, tasks[0].state)
    assertEquals(AiTaskQueueItemKind.LIBRARY_ORGANIZATION, tasks[1].kind)
    assertEquals("Book two", tasks[1].title)
    assertEquals(AiTaskQueueItemState.QUEUED, tasks[1].state)
    assertEquals(AiTaskQueueItemKind.SUMMARY, tasks[2].kind)
    assertEquals(AiTaskQueueItemState.RUNNING, tasks[2].state)
    assertEquals(AiTaskQueueProgressStage.CLOUD_GENERATING, tasks[2].progressStage)
  }

  @Test
  fun `ローカル停止は実行待ちの蔵書整理タスクを一時停止表示し再開時に再度キックする`() = runBlocking {
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.RUNNING))
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(
      summary,
      library,
      FakeLibraryCatalogRepository(testBooks()),
      scheduler,
    )

    repository.setLocalPaused(true)

    assertTrue(summary.execution.localPaused)
    assertFalse(summary.execution.cloudPaused)
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, library.batch!!.status)
    assertEquals(1, scheduler.cancelCount)
    assertTrue(scheduler.chargingResumeArmed)
    assertEquals(AiTaskQueueItemState.PAUSED, repository.listTasks()[1].state)

    repository.setLocalPaused(false)

    assertFalse(summary.execution.localPaused)
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, library.batch!!.status)
    assertEquals(1, scheduler.kickCount)
    assertFalse(scheduler.chargingResumeArmed)
  }

  @Test
  fun `クラウド停止はローカル蔵書整理を停止しない`() = runBlocking {
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.RUNNING))
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(
      summary,
      library,
      FakeLibraryCatalogRepository(testBooks()),
      scheduler,
    )

    repository.setCloudPaused(true)

    assertTrue(summary.execution.cloudPaused)
    assertFalse(summary.execution.localPaused)
    assertEquals(0, scheduler.cancelCount)
    assertEquals(AiTaskQueueItemState.QUEUED, repository.listTasks()[1].state)
  }

  @Test
  fun `個別に一時停止した蔵書整理はローカル停止の解除や充電再開対象にしない`() = runBlocking {
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = testBatch(LibraryOrganizationBatchStatus.PAUSED))
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(
      summary,
      library,
      FakeLibraryCatalogRepository(testBooks()),
      scheduler,
    )

    repository.setLocalPaused(true)
    repository.setLocalPaused(false)

    assertEquals(LibraryOrganizationBatchStatus.PAUSED, library.batch!!.status)
    assertEquals(0, scheduler.kickCount)
    assertFalse(scheduler.chargingResumeArmed)
  }

  @Test
  fun `失敗した蔵書整理タスクは一冊だけ再試行してワーカーをキックする`() = runBlocking {
    val failed = testBatch(LibraryOrganizationBatchStatus.COMPLETED).copy(
      candidates = testBatch(LibraryOrganizationBatchStatus.COMPLETED).candidates.mapIndexed { index, candidate ->
        if (index == 1) candidate.copy(status = LibraryOrganizationCandidateStatus.FAILED, error = "test failure") else candidate
      },
    )
    val summary = FakeSummaryRepository()
    val library = FakeLibraryOrganizationRepository(batch = failed)
    val scheduler = FakeScheduler()
    val repository = CompositeAiTaskQueueRepository(
      summary,
      library,
      FakeLibraryCatalogRepository(testBooks()),
      scheduler,
    )
    val task = repository.listTasks()[1]

    assertTrue(task.canResume)
    assertTrue(repository.resume(task.id))

    assertEquals(LibraryOrganizationCandidateStatus.QUEUED, library.batch!!.candidates[1].status)
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, library.batch!!.status)
    assertEquals(1, scheduler.kickCount)
  }
}

private class FakeSummaryRepository(
  private val tasks: List<SummaryQueueTask> = emptyList(),
) : SummaryTaskQueueRepository {
  var execution = SummaryQueueExecutionState(
    localPaused = false,
    cloudPaused = false,
    resumeLocalWhenCharging = true,
  )

  override suspend fun listTasks(): List<SummaryQueueTask> = tasks
  override suspend fun executionState(): SummaryQueueExecutionState = execution
  override suspend fun kick() = Unit
  override suspend fun setLocalPaused(paused: Boolean) {
    execution = execution.copy(localPaused = paused)
  }
  override suspend fun setCloudPaused(paused: Boolean) {
    execution = execution.copy(cloudPaused = paused)
  }
  override suspend fun setResumeLocalWhenCharging(enabled: Boolean) {
    execution = execution.copy(resumeLocalWhenCharging = enabled)
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
  override suspend fun retryCandidate(key: LibraryBookKey) {
    batch = batch?.let { current ->
      current.copy(
        status = LibraryOrganizationBatchStatus.RUNNING,
        candidates = current.candidates.map { candidate ->
          if (candidate.key == key) candidate.copy(status = LibraryOrganizationCandidateStatus.QUEUED, error = null) else candidate
        },
      )
    }
  }
}

private class FakeLibraryCatalogRepository(
  private val books: List<LibraryBook>,
) : LibraryRepository {
  override suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(
    books = books,
    hiddenBooks = emptyList(),
    sourceStates = emptyMap(),
  )

  override suspend fun hideBook(book: LibraryBook) = error("unused")
  override suspend fun restoreBook(book: LibraryBook) = error("unused")
  override suspend fun setBookSeries(book: LibraryBook, series: LibrarySeries) = error("unused")
  override suspend fun clearBookSeries(book: LibraryBook) = error("unused")
  override suspend fun syncGooglePlayBooks(accessToken: String, accountLabel: String?): LibrarySyncResult = error("unused")
  override suspend fun importAmazonLibraryJson(source: LibrarySource, json: String): LibrarySyncResult = error("unused")
}

private class FakeScheduler : LibraryOrganizationBatchScheduler {
  var kickCount = 0
  var cancelCount = 0
  var chargingResumeArmed = false

  override fun kick() {
    kickCount += 1
  }

  override suspend fun cancel() {
    cancelCount += 1
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    chargingResumeArmed = enabled
  }
}

private fun testBooks(): List<LibraryBook> = listOf(
  testBook("book-1", "Book one"),
  testBook("book-2", "Book two"),
)

private fun testBook(sourceId: String, title: String): LibraryBook = LibraryBook(
  source = LibrarySource.KINDLE,
  sourceId = sourceId,
  title = title,
  authors = emptyList(),
  publisher = null,
  publishedDate = null,
  description = null,
  isbn10 = null,
  isbn13 = null,
  thumbnailUrl = null,
  infoUrl = null,
)

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
