package dev.terashima.yomitorirss.feature.summary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkAiMetadataTest {
  @Test
  fun `生成タグの装飾を除去して重複をまとめる`() {
    assertEquals(
      listOf("Android", "ローカルAI", "Kotlin"),
      parseGeneratedTags("タグ: Android、ローカルAI\n- android\n3. Kotlin"),
    )
  }

  @Test
  fun `生成タグは最大5件に制限する`() {
    assertEquals(
      listOf("A", "B", "C", "D", "E"),
      parseGeneratedTags("A,B,C,D,E,F"),
    )
  }

  @Test
  fun `既存タグ候補を再利用する指示と一緒にプロンプトへ含める`() {
    val prompt = buildAutoTagPrompt(listOf("Android", "ローカルAI"))

    assertTrue(prompt.contains("既存タグ候補"))
    assertTrue(prompt.contains("- Android"))
    assertTrue(prompt.contains("- ローカルAI"))
    assertTrue(prompt.contains("完全に同じ表記で優先して使う"))
    assertTrue(prompt.contains("既存タグで表現できない概念だけ新しいタグを生成する"))
  }

  @Test
  fun `既存タグがない場合は候補一覧を追加しない`() {
    val prompt = buildAutoTagPrompt(emptyList())

    assertFalse(prompt.contains("既存タグ候補（"))
  }
}
