package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class WebLibraryMetadataExtractorEditorTest {
  @Test
  fun `実行テストの成功状態を適用成功として表示する`() {
    assertEquals(
      "適用成功",
      webLibraryMetadataExtractorTestStatusLabel(WebLibraryMetadataExtractorStatus.APPLIED),
    )
  }

  @Test
  fun `実行テストの失敗理由を区別して表示する`() {
    assertEquals(
      "Promise が reject",
      webLibraryMetadataExtractorTestStatusLabel(WebLibraryMetadataExtractorStatus.REJECTED),
    )
    assertEquals(
      "タイムアウト",
      webLibraryMetadataExtractorTestStatusLabel(WebLibraryMetadataExtractorStatus.TIMED_OUT),
    )
    assertEquals(
      "戻り値が不正",
      webLibraryMetadataExtractorTestStatusLabel(WebLibraryMetadataExtractorStatus.INVALID_RESULT),
    )
  }
}
