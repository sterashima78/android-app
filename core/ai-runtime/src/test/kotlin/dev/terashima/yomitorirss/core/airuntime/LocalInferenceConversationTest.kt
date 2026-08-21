package dev.terashima.yomitorirss.core.airuntime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
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
  fun `tool引数型をOpenAPI schemaへ反映する`() {
    val definition = LocalInferenceTool(
      name = "submit_book_metadata",
      description = "書誌情報を提出する",
      arguments = listOf(
        LocalInferenceToolArgument(
          "authors",
          "著者",
          required = true,
          type = LocalInferenceToolArgumentType.STRING_ARRAY,
        ),
        LocalInferenceToolArgument(
          "seriesPosition",
          "巻数",
          type = LocalInferenceToolArgumentType.INTEGER,
        ),
        LocalInferenceToolArgument(
          "confidence",
          "確信度",
          type = LocalInferenceToolArgumentType.NUMBER,
        ),
      ),
      execute = { "ok" },
    )

    val parameters = Json.parseToJsonElement(toolDescriptionJson(definition))
      .jsonObject.getValue("parameters").jsonObject
    val properties = parameters.getValue("properties").jsonObject

    assertFalse(parameters.getValue("additionalProperties").jsonPrimitive.boolean)
    assertEquals("array", properties.getValue("authors").jsonObject.getValue("type").jsonPrimitive.content)
    assertEquals(
      "string",
      properties.getValue("authors").jsonObject.getValue("items").jsonObject.getValue("type").jsonPrimitive.content,
    )
    assertEquals("integer", properties.getValue("seriesPosition").jsonObject.getValue("type").jsonPrimitive.content)
    assertEquals("number", properties.getValue("confidence").jsonObject.getValue("type").jsonPrimitive.content)
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
  fun `tool実行結果はJSONとして表現する`() {
    val result = Json.parseToJsonElement(toolResultJson("1行目\n2行目")).jsonObject

    assertEquals("1行目\n2行目", result.getValue("result").jsonPrimitive.content)
  }

  @Test
  fun `tool実行失敗は内部情報を含まないJSONとして表現する`() {
    val resultJson = toolErrorJson()
    val result = Json.parseToJsonElement(resultJson).jsonObject

    assertEquals("ツール実行に失敗しました。", result.getValue("error").jsonPrimitive.content)
    assertFalse(resultJson.contains("秘密の内部エラー"))
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
