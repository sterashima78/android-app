package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueItemState
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchSnapshot
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationItem
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMetadataNormalizationTaskQueueAdapterTest {
  @Test
  fun `global pause中はactive itemをpausedとして公開する`() = runBlocking {
    val repository = FakeSmbNormalizationRepository(batch(item(SmbMetadataNormalizationStatus.PROCESSING)))
    val scheduler = RecordingSmbNormalizationScheduler()
    val adapter = SmbMetadataNormalizationTaskQueueAdapter(repository, scheduler)

    val task = adapter.tasks(globalPaused = true).single()

    assertEquals(AiTaskQueueItemState.PAUSED, task.state)
    assertFalse(task.canResume)
  }

  @Test
  fun `pending reviewは件数を公開しfailedだけ再試行できる`() = runBlocking {
    val pending = item(SmbMetadataNormalizationStatus.PENDING_REVIEW)
    val failed = item(SmbMetadataNormalizationStatus.FAILED, sourceId = "failed")
    val repository = FakeSmbNormalizationRepository(batch(pending, failed))
    val scheduler = RecordingSmbNormalizationScheduler()
    val adapter = SmbMetadataNormalizationTaskQueueAdapter(repository, scheduler)

    val tasks = adapter.tasks(globalPaused = false)
    assertEquals(1, tasks.first { it.id.endsWith(":book-1") }.pendingReviewCount)
    assertTrue(adapter.resume("smb-metadata-normalization:batch-1:failed", globalPaused = false) == true)
    assertEquals(listOf("failed"), repository.retried)
    assertEquals(1, scheduler.kickCount)
  }

  @Test
  fun `active workがあるrunning batchだけglobal gateで停止する`() = runBlocking {
    val repository = FakeSmbNormalizationRepository(batch(item(SmbMetadataNormalizationStatus.QUEUED)))
    val scheduler = RecordingSmbNormalizationScheduler()
    val adapter = SmbMetadataNormalizationTaskQueueAdapter(repository, scheduler)

    adapter.pauseForGlobalGate()

    assertEquals(1, scheduler.cancelCount)
    assertEquals(listOf(true), scheduler.resumeOnChargingValues)
  }

  private fun batch(vararg items: SmbMetadataNormalizationItem) = SmbMetadataNormalizationBatchSnapshot(
    batchId = "batch-1",
    status = SmbMetadataNormalizationBatchStatus.RUNNING,
    items = items.toList(),
    createdAtEpochMillis = 1L,
    updatedAtEpochMillis = 2L,
  )

  private fun item(
    status: SmbMetadataNormalizationStatus,
    sourceId: String = "book-1",
  ) = SmbMetadataNormalizationItem(
    batchId = "batch-1",
    sourceId = sourceId,
    originalFileName = "$sourceId.epub",
    inputSize = 1L,
    inputModifiedAt = 1L,
    status = status,
    updatedAtEpochMillis = 2L,
  )
}

private class FakeSmbNormalizationRepository(
  var batch: SmbMetadataNormalizationBatchSnapshot?,
) : SmbMetadataNormalizationRepository {
  val retried = mutableListOf<String>()

  override suspend fun batchSnapshot() = batch
  override suspend fun startBatch(books: List<LibraryBook>) = books.size
  override suspend fun applyCandidate(
    sourceId: String,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  ) = Unit
  override suspend fun deferCandidate(sourceId: String) = Unit
  override suspend fun rejectCandidate(sourceId: String) = Unit
  override suspend fun reopenCandidate(sourceId: String) = Unit
  override suspend fun retryCandidate(sourceId: String, supplementalContext: String?) {
    retried += sourceId
  }
}

private class RecordingSmbNormalizationScheduler : SmbMetadataNormalizationScheduler {
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
