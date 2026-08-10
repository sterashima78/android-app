package dev.terashima.yomitorirss.core.airuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {
  @Test
  fun `会話が長い場合は新しい発言を優先する`() {
    val turns = listOf(
      ChatTurn(ChatRole.USER, "古い質問".repeat(20)),
      ChatTurn(ChatRole.ASSISTANT, "古い回答".repeat(20)),
      ChatTurn(ChatRole.USER, "最新の質問"),
    )

    val trimmed = ChatPrompt.trimTurns(turns, 70)

    assertEquals(ChatRole.USER, trimmed.last().role)
    assertEquals("最新の質問", trimmed.last().content)
    assertFalse(trimmed.any { it.content.startsWith("古い質問") })
  }

  @Test
  fun `ChatMLではsystemと会話ロールを組み立てる`() {
    val prompt = ChatPrompt.render(
      turns = listOf(
        ChatTurn(ChatRole.USER, "こんにちは"),
        ChatTurn(ChatRole.ASSISTANT, "こんにちは。"),
        ChatTurn(ChatRole.USER, "今日の話題は？"),
      ),
      context = emptyList(),
      maxInputChars = 1200,
      chatMl = true,
    )

    assertTrue(prompt.contains("<|im_start|>system"))
    assertTrue(prompt.contains("<|im_start|>user\n今日の話題は？"))
    assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
  }

  @Test
  fun `参照情報をプロンプトへ明示的に含める`() {
    val prompt = ChatPrompt.render(
      turns = listOf(ChatTurn(ChatRole.USER, "この記事について教えて")),
      context = listOf(ChatContextBlock("article-1", "記事", "参照本文")),
      maxInputChars = 1200,
      chatMl = false,
    )

    assertTrue(prompt.contains("参照情報:"))
    assertTrue(prompt.contains("[記事]"))
    assertTrue(prompt.contains("参照本文"))
  }
}
