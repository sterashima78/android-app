package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizationBatchTest {
  @Test
  fun `解析進捗は解析待ちと解析中だけを未完了として数える`() {
    val batch = batchOf(
      LibraryOrganizationCandidateStatus.QUEUED,
      LibraryOrganizationCandidateStatus.PROCESSING,
      LibraryOrganizationCandidateStatus.APPLIED,
      LibraryOrganizationCandidateStatus.REJECTED,
      LibraryOrganizationCandidateStatus.FAILED,
      LibraryOrganizationCandidateStatus.SKIPPED,
    )

    assertEquals(6, batch.total)
    assertEquals(4, batch.processed)
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
