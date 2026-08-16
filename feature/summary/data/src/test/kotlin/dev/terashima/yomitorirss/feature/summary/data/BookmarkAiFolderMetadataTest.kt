package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkAiFolderMetadataTest {
  @Test
  fun `既存フォルダ候補だけを選択する`() {
    assertEquals(
      "Android",
      parseGeneratedFolder("フォルダ: android", listOf("Android", "AI")),
    )
  }

  @Test
  fun `候補外のフォルダ名は採用しない`() {
    assertNull(parseGeneratedFolder("新しい分類", listOf("Android", "AI")))
  }

  @Test
  fun `適切な候補がない応答は未分類のままにする`() {
    assertNull(parseGeneratedFolder("なし", listOf("Android", "AI")))
    assertNull(parseGeneratedFolder("未分類", listOf("Android", "AI")))
  }
}
