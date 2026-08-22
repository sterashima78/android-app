package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbCoverPrefetchDisplayTest {
  @Test
  fun `表紙先読みキューでは完了済みジョブを個別表示しない`() {
    val items = listOf(
      item("running", SmbCoverPrefetchStatus.RUNNING),
      item("completed", SmbCoverPrefetchStatus.COMPLETED),
      item("pending", SmbCoverPrefetchStatus.PENDING),
      item("failed", SmbCoverPrefetchStatus.FAILED),
      item("skipped", SmbCoverPrefetchStatus.SKIPPED),
    )

    val visible = visibleSmbCoverPrefetchItems(items)

    assertEquals(
      listOf("running", "pending", "failed", "skipped"),
      visible.map(SmbCoverPrefetchItem::sourceId),
    )
  }

  @Test
  fun `完了済みジョブだけなら個別表示は空になる`() {
    val visible = visibleSmbCoverPrefetchItems(
      listOf(item("completed", SmbCoverPrefetchStatus.COMPLETED)),
    )

    assertEquals(emptyList<SmbCoverPrefetchItem>(), visible)
  }

  private fun item(
    sourceId: String,
    status: SmbCoverPrefetchStatus,
  ): SmbCoverPrefetchItem = SmbCoverPrefetchItem(
    sourceId = sourceId,
    title = sourceId,
    status = status,
    downloadedBytes = 0L,
    totalBytes = 0L,
    message = null,
    updatedAtEpochMillis = 1L,
  )
}
