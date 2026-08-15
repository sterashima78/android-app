package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizationBatchTest {
  @Test
  fun `一括解析では既存分類と同名の候補を重複追加しない`() {
    val names = mutableListOf("Android", "仕事")

    addDistinctOrganizationNames(
      names,
      listOf(" android ", "LLM", "llm", "  ", "設計"),
    )

    assertEquals(listOf("Android", "仕事", "LLM", "設計"), names)
  }

  @Test
  fun `一括解析で生成した分類を後続候補として追加できる`() {
    val names = mutableListOf("既存")

    addDistinctOrganizationNames(names, listOf("新規A"))
    addDistinctOrganizationNames(names, listOf("新規A", "新規B"))

    assertEquals(listOf("既存", "新規A", "新規B"), names)
  }
}
