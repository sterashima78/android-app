package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryCoverAcquisitionState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCoverStatusRepositoryTest {
  @Test
  fun `元データの表紙を最優先する`() {
    assertEquals(
      LibraryCoverAcquisitionState.SOURCE_PROVIDED,
      resolveLibraryCoverAcquisitionState("source", "external", CoverLookupStatus.FOUND.name, 900L, 500L),
    )
  }

  @Test
  fun `外部取得済みの表紙を取得済みとして扱う`() {
    assertEquals(
      LibraryCoverAcquisitionState.ACQUIRED,
      resolveLibraryCoverAcquisitionState(null, "external", CoverLookupStatus.FOUND.name, 900L, 500L),
    )
  }

  @Test
  fun `最近見つからなかった項目は未取得として保持する`() {
    assertEquals(
      LibraryCoverAcquisitionState.NOT_FOUND,
      resolveLibraryCoverAcquisitionState(null, null, CoverLookupStatus.NOT_FOUND.name, 900L, 500L),
    )
  }

  @Test
  fun `取得エラーは未取得として再試行可能にする`() {
    assertEquals(
      LibraryCoverAcquisitionState.NOT_FOUND,
      resolveLibraryCoverAcquisitionState(null, null, CoverLookupStatus.ERROR.name, 900L, 500L),
    )
  }

  @Test
  fun `古い未取得結果は待機状態へ戻す`() {
    assertEquals(
      LibraryCoverAcquisitionState.WAITING,
      resolveLibraryCoverAcquisitionState(null, null, CoverLookupStatus.AMBIGUOUS.name, 100L, 500L),
    )
  }
}
