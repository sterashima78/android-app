package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookKey
import dev.terashima.yomitorirss.feature.library.LibraryBookSeriesUpdate
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTaskQueueAdapterTest {
  @Test
  fun `global pause中は待機中candidateをpausedとして公開する`() = runBlocking {
    val candidate = candidate(status = LibraryOrganizationCandidateStatus.QUEUED)
    val repository = FakeOrganizationRepository(batch(candidate))
    val scheduler = RecordingOrganizationScheduler()
    val adapter = LibraryTaskQueueAdapter(
      repository = repository,
      catalogRepository = FakeLibraryRepository(listOf(book())),
      scheduler = scheduler,
    )

    val task = adapter.tasks(globalPaused = true).single()

    assertEquals(AiTaskQueueItemState.PAUSED, task.state)
    assertEquals("対象書籍", task.title)
    assertFalse(task.canResume)
  }

  @Test
  fun `失敗candidateだけを再試行してschedulerを起動する`() = runBlocking {
    val failed = candidate(status = LibraryOrganizationCandidateStatus.FAILED)
    val repository = FakeOrganizationRepository(batch(failed))
    val scheduler = RecordingOrganizationScheduler()
    val adapter = LibraryTaskQueueAdapter(
      repository = repository,
      catalogRepository = FakeLibraryRepository(listOf(book())),
      scheduler = scheduler,
    )

    assertTrue(adapter.resume("library-organization:batch-1:KINDLE:book-1", globalPaused = false) == true)
    assertEquals(listOf(failed.key), repository.retried)
    assertEquals(1, scheduler.kickCount)
    assertTrue(adapter.resume("other:task", globalPaused = false) == null)
  }

  @Test
  fun `running batchをglobal gateで停止すると充電時再開を予約する`() = runBlocking {
    val repository = FakeOrganizationRepository(batch(candidate(LibraryOrganizationCandidateStatus.PROCESSING)))
    val scheduler = RecordingOrganizationScheduler()
    val adapter = LibraryTaskQueueAdapter(
      repository = repository,
      catalogRepository = FakeLibraryRepository(listOf(book())),
      scheduler = scheduler,
    )

    adapter.pauseForGlobalGate(LibraryOrganizationBatchStatus.RUNNING)

    assertEquals(1, scheduler.cancelCount)
    assertEquals(listOf(true), scheduler.resumeOnChargingValues)
  }

  private fun batch(candidate: LibraryOrganizationCandidate) = LibraryOrganizationBatchSnapshot(
    batchId = "batch-1",
    status = LibraryOrganizationBatchStatus.RUNNING,
    candidates = listOf(candidate),
    createdAt = 1L,
    updatedAt = 2L,
  )

  private fun candidate(status: LibraryOrganizationCandidateStatus) = LibraryOrganizationCandidate(
    batchId = "batch-1",
    key = LibraryBookKey(LibrarySource.KINDLE, "book-1"),
    status = status,
    error = "error".takeIf { status == LibraryOrganizationCandidateStatus.FAILED },
    updatedAt = 2L,
  )

  private fun book() = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = "book-1",
    title = "対象書籍",
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )
}

private class FakeOrganizationRepository(
  var batch: LibraryOrganizationBatchSnapshot?,
) : LibraryOrganizationRepository {
  val retried = mutableListOf<LibraryBookKey>()

  override suspend fun snapshot() = LibraryOrganizationSnapshot()
  override suspend fun save(book: LibraryBook, draft: LibraryOrganizationDraft) = Unit
  override suspend fun batchSnapshot() = batch
  override suspend fun startBatch(books: List<LibraryBook>) = "batch-1"
  override suspend fun pauseBatch() = Unit
  override suspend fun resumeBatch() = Unit
  override suspend fun rejectCandidate(key: LibraryBookKey) = Unit
  override suspend fun retryCandidate(key: LibraryBookKey) {
    retried += key
  }
}

private class FakeLibraryRepository(
  private val books: List<LibraryBook>,
) : LibraryRepository {
  override suspend fun snapshot() = LibrarySnapshot(books, emptyList(), emptyMap())
  override suspend fun hideBook(book: LibraryBook) = Unit
  override suspend fun restoreBook(book: LibraryBook) = Unit
  override suspend fun setBookSeries(book: LibraryBook, series: LibrarySeries) = Unit
  override suspend fun setBookSeries(updates: List<LibraryBookSeriesUpdate>) = Unit
  override suspend fun clearBookSeries(book: LibraryBook) = Unit
  override suspend fun syncGooglePlayBooks(accessToken: String, accountLabel: String?) =
    LibrarySyncResult(0, 0L)
  override suspend fun importAmazonLibraryJson(source: LibrarySource, json: String) =
    LibrarySyncResult(0, 0L)
}

private class RecordingOrganizationScheduler : LibraryOrganizationBatchScheduler {
  var kickCount = 0
  var cancelCount = 0
  val resumeOnChargingValues = mutableListOf<Boolean>()

  override fun kick() {
    kickCount += 1
  }

  override suspend fun cancel() {
    cancelCount += 1
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    resumeOnChargingValues += enabled
  }
}
