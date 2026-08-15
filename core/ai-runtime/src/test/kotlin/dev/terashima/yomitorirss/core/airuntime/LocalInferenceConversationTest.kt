package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInferenceConversationTest {
  @Test
  fun `tool定義をOpenAPI形式へ変換する`() {
    val definition = LocalInferenceTool(
      name = "search_saved_articles",
      description = "保存済み記事を検索する",
      arguments = listOf(
        LocalInferenceToolArgument("query", "検索語"),
        LocalInferenceToolArgument("limit", "最大件数", required = true),
      ),
      execute = { "ok" },
    )

    val schema = toolDescriptionJson(definition)

    assertTrue(schema.contains("\"name\":\"search_saved_articles\""))
    assertTrue(schema.contains("\"query\""))
    assertTrue(schema.contains("\"limit\""))
    assertTrue(schema.contains("\"required\":[\"limit\"]"))
  }

  @Test
  fun `tool引数は文字列へ正規化する`() {
    val arguments = parseToolArguments(
      """{"query":"Kotlin","limit":5,"nested":{"key":"value"}}""",
    )

    assertEquals("Kotlin", arguments["query"])
    assertEquals("5", arguments["limit"])
    assertEquals("{\"key\":\"value\"}", arguments["nested"])
  }

  @Test
  fun `不正なtool引数JSONは空引数として扱う`() {
    assertTrue(parseToolArguments("broken").isEmpty())
  }

  @Test
  fun `tool名の重複を拒否する`() {
    val tool = LocalInferenceTool("same", "test", execute = { "ok" })
    val result = runCatching {
      LocalInferenceConversationRequest(
        systemInstruction = "system",
        initialMessages = emptyList(),
        userMessage = "user",
        tools = listOf(tool, tool),
      )
    }

    assertFalse(result.isSuccess)
  }
}
