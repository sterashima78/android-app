package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizationBatchTest {
  @Test
  fun `解析進捗は解析待ちと解析中を未完了として数える`() {
    val batch = batchOf(
      LibraryOrganizationCandidateStatus.QUEUED,
      LibraryOrganizationCandidateStatus.PROCESSING,
      LibraryOrganizationCandidateStatus.PENDING_REVIEW,
      LibraryOrganizationCandidateStatus.APPLIED,
      LibraryOrganizationCandidateStatus.FAILED,
    )

    assertEquals(5, batch.total)
    assertEquals(3, batch.processed)
  }

  @Test
  fun `未確認と保留は独立して仕分け件数を数える`() {
    val batch = batchOf(
      LibraryOrganizationCandidateStatus.PENDING_REVIEW,
      LibraryOrganizationCandidateStatus.PENDING_REVIEW,
      LibraryOrganizationCandidateStatus.DEFERRED,
      LibraryOrganizationCandidateStatus.REJECTED,
    )

    assertEquals(2, batch.pendingReview)
    assertEquals(1, batch.deferred)
  }
}

private fun batchOf(vararg statuses: LibraryOrganizationCandidateStatus): LibraryOrganizationBatchSnapshot =
  LibraryOrganizationBatchSnapshot(
    batchId = "batch-test",
    status = LibraryOrganizationBatchStatus.RUNNING,
    candidates = statuses.mapIndexed { index, status ->
      LibraryOrganizationCandidate(
        batchId = "batch-test",
        key = LibraryBookKey(LibrarySource.KINDLE, "book-$index"),
        status = status,
        updatedAt = index.toLong(),
      )
    },
    createdAt = 1L,
    updatedAt = 2L,
  )
