package dev.terashima.yomitorirss.feature.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatResponseStreamTest {
  @Test
  fun `受信途中の応答をそのまま表示用に整形する`() {
    assertEquals("こんにちは", ChatResponseStream.partial("こんにちは"))
    assertEquals("こんにちは、世界", ChatResponseStream.partial("こんにちは、世界"))
  }

  @Test
  fun `思考部分は閉じるまで表示しない`() {
    assertEquals("", ChatResponseStream.partial("<think>検討中"))
    assertEquals("回答", ChatResponseStream.partial("<think>検討中</think>回答"))
  }

  @Test
  fun `アシスタント接頭辞は途中から画面に出さない`() {
    assertEquals("", ChatResponseStream.partial("アシス"))
    assertEquals("回答", ChatResponseStream.partial("アシスタント： 回答"))
  }

  @Test
  fun `制御トークンの途中断片を表示しない`() {
    assertEquals("回答", ChatResponseStream.partial("回答<|im_"))
    assertEquals("回答", ChatResponseStream.complete("回答<|im_end|>後続"))
  }
}
