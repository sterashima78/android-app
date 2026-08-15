package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLibraryOrganizationSuggesterTest {
  @Test
  fun `AI suggestion parser accepts surrounding text and deduplicates labels`() {
    val suggestion = parseLibraryOrganizationSuggestion(
      """
        ```json
        {"tags":["Android"," android ","設計"],"collections":["技術","技術"],"reason":"書誌情報から判断"}
        ```
      """.trimIndent(),
    )

    assertEquals(listOf("Android", "設計"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals("書誌情報から判断", suggestion.reason)
  }

  @Test
  fun `AI suggestion parser limits generated taxonomy size`() {
    val suggestion = parseLibraryOrganizationSuggestion(
      """{"tags":["1","2","3","4","5","6"],"collections":["A","B","C"]}""",
    )

    assertEquals(listOf("1", "2", "3", "4", "5"), suggestion.tagNames)
    assertEquals(listOf("A", "B"), suggestion.collectionNames)
  }
}
