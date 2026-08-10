package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.chat.AgentSkill
import dev.terashima.yomitorirss.feature.chat.AgentTool
import dev.terashima.yomitorirss.feature.chat.AgentToolDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {
  @Test
  fun `tool call JSON を解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      <tool_call>
      {"name":"search_tasks","arguments":{"query":"請求","limit":"5"}}
      </tool_call>
      """.trimIndent(),
    )

    assertEquals("search_tasks", call?.name)
    assertEquals("請求", call?.arguments?.get("query"))
    assertEquals("5", call?.arguments?.get("limit"))
  }

  @Test
  fun `説明付き fenced JSON の tool_name も tool call として解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      `bookmark-reader` スキルを使用して検索します。

      `search_saved_articles` ツールを呼び出します。

      ```json
      {
        "tool_name": "search_saved_articles",
        "arguments": {
          "query": "はてなブックマーク"
        }
      }
      ```
      """.trimIndent(),
    )

    assertEquals("search_saved_articles", call?.name)
    assertEquals("はてなブックマーク", call?.arguments?.get("query"))
  }

  @Test
  fun `単独 JSON の tool_name も tool call として解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      {"tool_name":"search_saved_articles","parameters":{"query":"はてなブックマーク"}}
      """.trimIndent(),
    )

    assertEquals("search_saved_articles", call?.name)
    assertEquals("はてなブックマーク", call?.arguments?.get("query"))
  }

  @Test
  fun `波括弧を含む引数でも tool call JSON を解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      <tool_call>
      {"name":"search_tasks","arguments":{"query":"請求 {2026}","filter":"{open}"}}
      </tool_call>
      """.trimIndent(),
    )

    assertEquals("search_tasks", call?.name)
    assertEquals("請求 {2026}", call?.arguments?.get("query"))
    assertEquals("{open}", call?.arguments?.get("filter"))
  }

  @Test
  fun `終了マーカーを含む引数でも tool call JSON を解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      <tool_call>
      {"name":"search_tasks","arguments":{"query":"literal </tool_call> marker"}}
      </tool_call>
      """.trimIndent(),
    )

    assertEquals("search_tasks", call?.name)
    assertEquals("literal </tool_call> marker", call?.arguments?.get("query"))
  }

  @Test
  fun `大文字小文字が異なる tool call マーカーも解析できる`() {
    val call = AgentProtocol.parseToolCall(
      """
      <TOOL_CALL>
      {"name":"search_tasks","arguments":{}}
      </TOOL_CALL>
      """.trimIndent(),
    )

    assertEquals("search_tasks", call?.name)
  }

  @Test
  fun `不正な tool call は解析しない`() {
    assertNull(AgentProtocol.parseToolCall("<tool_call>{broken}</tool_call>"))
    assertTrue(AgentProtocol.containsToolCallMarker("<tool_call>{broken}</tool_call>"))
  }

  @Test
  fun `tool call のストリーミング途中はユーザー表示から隠す`() {
    assertTrue(AgentProtocol.shouldHideFromUser("<"))
    assertTrue(AgentProtocol.shouldHideFromUser("<tool_call>"))
    assertFalse(AgentProtocol.shouldHideFromUser("通常の回答です"))
  }

  @Test
  fun `既知の skill 名から始まる tool narration はユーザー表示から隠す`() {
    val agentNames = setOf("bookmark-reader", "search_saved_articles")

    assertTrue(AgentProtocol.shouldHideFromUser("`b", agentNames))
    assertTrue(
      AgentProtocol.shouldHideFromUser(
        "`bookmark-reader` スキルを使用して検索します。",
        agentNames,
      ),
    )
    assertTrue(
      AgentProtocol.shouldHideFromUser(
        """
        `bookmark-reader` スキルを使用して検索します。
        ```json
        {"tool_name":"search_saved_articles","arguments":{"query":"はてなブックマーク"}}
        ```
        """.trimIndent(),
        agentNames,
      ),
    )
    assertFalse(AgentProtocol.shouldHideFromUser("ブックマーク記事は3件あります。", agentNames))
  }

  @Test
  fun `skill と tool 定義をコンテキストに含める`() {
    val skill = AgentSkill(
      name = "test-skill",
      description = "test description",
      instructions = "test instructions",
      tools = listOf(TestTool),
    )

    val context = AgentProtocol.renderContext(listOf(skill), emptyList())

    assertTrue(context.contains("test-skill"))
    assertTrue(context.contains("test_tool"))
    assertTrue(context.contains("<tool_call>"))
    assertTrue(context.contains("tool_name ではなく必ず name"))
  }

  private object TestTool : AgentTool {
    override val definition = AgentToolDefinition(
      name = "test_tool",
      description = "test tool",
    )

    override suspend fun execute(arguments: Map<String, String>): String = "ok"
  }
}
