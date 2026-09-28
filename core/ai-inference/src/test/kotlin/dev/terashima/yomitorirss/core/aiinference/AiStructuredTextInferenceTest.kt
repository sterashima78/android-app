package dev.terashima.yomitorirss.core.aiinference

import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AiStructuredTextInferenceTest {
  @Test
  fun `tool schema preserves typed required arguments`() {
    val tool = AiStructuredTool(
      name = "submit_result",
      description = "結果を提出する",
      arguments = listOf(
        AiStructuredToolArgument(
          name = "labels",
          description = "ラベル配列",
          required = true,
          type = AiStructuredToolArgumentType.STRING_ARRAY,
        ),
      ),
    )

    assertEquals("submit_result", tool.name)
    assertFalse(tool.allowAdditionalArguments)
    assertEquals(AiStructuredToolArgumentType.STRING_ARRAY, tool.arguments.single().type)
  }

  @Test
  fun `background scope外ではstructured生成実装を呼び出さない`() {
    var generated = false
    val inference = object : BackgroundAiStructuredTextInference() {
      override suspend fun generateToolCallInBackground(
        systemInstruction: String,
        userMessage: String,
        tool: AiStructuredTool,
      ): AiStructuredToolCall? {
        generated = true
        return null
      }
    }
    val tool = AiStructuredTool(
      name = "submit_result",
      description = "結果を提出する",
      arguments = emptyList(),
    )

    assertThrows(IllegalStateException::class.java) {
      runBlocking {
        inference.generateToolCall("system", "user", tool)
      }
    }
    assertFalse(generated)
  }

  @Test
  fun `tool rejects duplicate argument names`() {
    assertThrows(IllegalArgumentException::class.java) {
      AiStructuredTool(
        name = "submit_result",
        description = "結果を提出する",
        arguments = listOf(
          AiStructuredToolArgument("value", "値"),
          AiStructuredToolArgument("value", "重複値"),
        ),
      )
    }
  }

  @Test
  fun `tool argument rejects blank description`() {
    assertThrows(IllegalArgumentException::class.java) {
      AiStructuredToolArgument(name = "value", description = " ")
    }
  }
}
