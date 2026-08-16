package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkAiMetadataTest {
  @Test
  fun `生成タグの装飾を除去して重複をまとめる`() {
    assertEquals(
      listOf("Android", "ローカルAI", "Kotlin"),
      normalizeGeneratedTags(listOf("タグ: Android", "ローカルAI", "- android", "3. Kotlin")),
    )
  }

  @Test
  fun `生成タグは最大5件に制限する`() {
    assertEquals(
      listOf("A", "B", "C", "D", "E"),
      normalizeGeneratedTags(listOf("A", "B", "C", "D", "E", "F")),
    )
  }
}
