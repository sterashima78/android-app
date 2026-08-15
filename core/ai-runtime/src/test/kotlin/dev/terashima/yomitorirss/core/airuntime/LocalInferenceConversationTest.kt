package dev.terashima.yomitorirss.core.airuntime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
  fun `不正なtool引数JSONは拒否する`() {
    assertTrue(runCatching { parseToolArguments("broken") }.isFailure)
  }

  @Test
  fun `tool実行結果はJSONとして返す`() {
    val tool = LocalOpenApiTool(
      LocalInferenceTool(
        name = "test_tool",
        description = "test",
        execute = { "1行目\n2行目" },
      ),
    )

    val result = Json.parseToJsonElement(tool.execute("{}")).jsonObject

    assertEquals("1行目\n2行目", result.getValue("result").jsonPrimitive.content)
  }

  @Test
  fun `tool実行失敗もJSONとして返す`() {
    val tool = LocalOpenApiTool(
      LocalInferenceTool(
        name = "test_tool",
        description = "test",
        execute = { error("秘密の内部エラー") },
      ),
    )

    val result = Json.parseToJsonElement(tool.execute("{}")).jsonObject

    assertEquals("ツール実行に失敗しました。", result.getValue("error").jsonPrimitive.content)
    assertFalse(tool.execute("{}").contains("秘密の内部エラー"))
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
