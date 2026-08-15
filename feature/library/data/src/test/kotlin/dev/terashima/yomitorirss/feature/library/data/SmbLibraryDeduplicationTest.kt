package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbLibraryDeduplicationTest {
  @Test
  fun `同じ書名とサイズと更新時刻のファイルは一冊だけ残す`() {
    val redundant = redundantSmbSourceIds(
      listOf(
        candidate(
          sourceId = "nested",
          title = "Sample Book",
          path = "archive\\Sample Book.pdf",
        ),
        candidate(
          sourceId = "root",
          title = " sample book ",
          path = "Sample Book.pdf",
        ),
      ),
    )

    assertEquals(listOf("nested"), redundant)
  }

  @Test
  fun `更新時刻が異なるファイルは同名同サイズでも別書籍として残す`() {
    val redundant = redundantSmbSourceIds(
      listOf(
        candidate(sourceId = "old", modifiedAt = 100L),
        candidate(sourceId = "new", modifiedAt = 200L),
      ),
    )

    assertEquals(emptyList<String>(), redundant)
  }

  @Test
  fun `形式が異なるファイルは同じメタデータでも別書籍として残す`() {
    val redundant = redundantSmbSourceIds(
      listOf(
        candidate(sourceId = "pdf", format = "PDF"),
        candidate(sourceId = "zip", format = "ZIP"),
      ),
    )

    assertEquals(emptyList<String>(), redundant)
  }

  @Test
  fun `重複時は入力順に依存せず浅いパスを残す`() {
    val deep = candidate(sourceId = "deep", path = "backup\\2026\\Sample Book.pdf")
    val shallow = candidate(sourceId = "shallow", path = "books\\Sample Book.pdf")

    assertEquals(listOf("deep"), redundantSmbSourceIds(listOf(deep, shallow)))
    assertEquals(listOf("deep"), redundantSmbSourceIds(listOf(shallow, deep)))
  }

  private fun candidate(
    sourceId: String,
    title: String = "Sample Book",
    path: String = "$sourceId.pdf",
    size: Long = 123_456L,
    modifiedAt: Long = 1_000L,
    format: String = "PDF",
  ) = SmbLibraryDeduplicationCandidate(
    sourceId = sourceId,
    title = title,
    path = path,
    size = size,
    modifiedAt = modifiedAt,
    format = format,
  )
}
