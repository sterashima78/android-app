package dev.terashima.yomitorirss.core.airuntime
import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingModeTest {
  @Test
  fun `Thinking有効時はthinkディレクティブを付ける`() {
    assertEquals("/think\n質問", ThinkingMode.apply("質問", enabled = true))
  }

  @Test
  fun `Thinking無効時はno thinkディレクティブを付ける`() {
    assertEquals("/no_think\n質問", ThinkingMode.apply("質問", enabled = false))
  }
}
