package dev.terashima.yomitorirss.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
  @Test
  fun `見出し 段落 リスト コードをブロックへ分解する`() {
    val blocks = parseMarkdownBlocks(
      """
      # 見出し

      本文です。

      - 項目1
      - [x] 完了

      ```kotlin
      val answer = 42
      ```
      """.trimIndent(),
    )

    assertEquals(MarkdownBlock.Heading(1, "見出し"), blocks[0])
    assertEquals(MarkdownBlock.Paragraph("本文です。"), blocks[1])
    assertEquals(
      MarkdownBlock.ListBlock(
        ordered = false,
        items = listOf(
          MarkdownListItem(indent = 0, marker = "-", text = "項目1"),
          MarkdownListItem(indent = 0, marker = "-", text = "[x] 完了"),
        ),
      ),
      blocks[2],
    )
    assertEquals(
      MarkdownBlock.CodeBlock(language = "kotlin", code = "val answer = 42"),
      blocks[3],
    )
  }

  @Test
  fun `Markdownテーブルを表として解釈する`() {
    val blocks = parseMarkdownBlocks(
      """
      | 名前 | 値 |
      | --- | ---: |
      | alpha | 1 |
      | beta | 2 |
      """.trimIndent(),
    )

    assertEquals(
      MarkdownBlock.Table(
        headers = listOf("名前", "値"),
        rows = listOf(
          listOf("alpha", "1"),
          listOf("beta", "2"),
        ),
      ),
      blocks.single(),
    )
  }

  @Test
  fun `ストリーミング途中の閉じていないコードフェンスもコードとして保持する`() {
    val blocks = parseMarkdownBlocks(
      """
      ```json
      {"status":"streaming"}
      """.trimIndent(),
    )

    assertEquals(
      MarkdownBlock.CodeBlock(language = "json", code = "{\"status\":\"streaming\"}"),
      blocks.single(),
    )
  }

  @Test
  fun `クリック可能なリンクのスキームを制限する`() {
    assertTrue(isSafeMarkdownLink("https://example.com"))
    assertTrue(isSafeMarkdownLink("http://example.com"))
    assertTrue(isSafeMarkdownLink("mailto:user@example.com"))
    assertFalse(isSafeMarkdownLink("javascript:alert(1)"))
    assertFalse(isSafeMarkdownLink("intent://example"))
  }
}
