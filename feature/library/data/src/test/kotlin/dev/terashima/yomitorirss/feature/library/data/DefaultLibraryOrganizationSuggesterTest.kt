package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgumentType
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceModel
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceProgress
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSeriesContext
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLibraryOrganizationSuggesterTest {
  @Test
  fun `AI suggestion parser validates tool arguments and deduplicates labels`() {
    val suggestion = parseLibraryOrganizationSuggestion(
      mapOf(
        "tags" to """["Android"," android ","設計"]""",
        "collections" to """["技術","技術"]""",
        "reason" to "書誌情報から判断",
      ),
    )

    assertEquals(listOf("Android", "設計"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals("書誌情報から判断", suggestion.reason)
  }

  @Test
  fun `AI suggestion parser rejects non array tags`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseLibraryOrganizationSuggestion(
        mapOf(
          "tags" to "「Android」「設計」",
          "collections" to """["技術"]""",
          "reason" to "test",
        ),
      )
    }

    assertTrue(error.message.orEmpty().contains("tags は文字列配列"))
  }

  @Test
  fun `AI suggestion parser rejects values beyond schema limits`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseLibraryOrganizationSuggestion(
        mapOf(
          "tags" to """["1","2","3","4","5","6"]""",
          "collections" to """["A"]""",
          "reason" to "test",
        ),
      )
    }

    assertTrue(error.message.orEmpty().contains("tags は最大 5 件"))
  }

  @Test
  fun `invalid tool arguments are regenerated once with validation feedback`() = runBlocking {
    val outputs = ArrayDeque(
      listOf(
        AiStructuredToolCall(
          name = "submit_library_organization",
          arguments = mapOf(
            "tags" to "Android",
            "collections" to "[]",
            "reason" to "invalid",
          ),
        ),
        AiStructuredToolCall(
          name = "submit_library_organization",
          arguments = mapOf(
            "tags" to """["Android"]""",
            "collections" to """["技術"]""",
            "reason" to "valid",
          ),
        ),
      ),
    )
    val prompts = mutableListOf<String>()

    val suggestion = generateValidatedLibraryOrganizationSuggestion(
      initialPrompt = "classify this book",
      promptBudgetChars = 2_000,
    ) { prompt ->
      prompts += prompt
      outputs.removeFirst()
    }

    assertEquals(listOf("Android"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals(2, prompts.size)
    assertTrue(prompts[1].contains("submit_library_organization 呼び出しは検証に失敗"))
    assertTrue(prompts[1].contains("tags は文字列配列"))
  }

  @Test
  fun `suggester uses provider neutral model budget and structured tool call`() = runBlocking {
    val textInference = FakeTextInference(promptBudgetChars = 8_000)
    val structuredInference = FakeStructuredTextInference(
      response = AiStructuredToolCall(
        name = "submit_library_organization",
        arguments = mapOf(
          "tags" to """["Android"]""",
          "collections" to """["技術"]""",
          "reason" to "valid",
        ),
      ),
    )

    val suggestion = DefaultLibraryOrganizationSuggester(
      textInference = textInference,
      structuredInference = structuredInference,
    ).suggest(
      book = testBook(),
      existingTags = listOf("Android"),
      existingCollections = listOf("技術"),
      seriesContext = null,
    )

    assertEquals(listOf("Android"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals(0, textInference.generateCalls)
    assertEquals(1, structuredInference.requests.size)
    val request = structuredInference.requests.single()
    assertTrue(request.userMessage.length <= 8_000)
    assertEquals("submit_library_organization", request.tool.name)
    assertFalse(request.tool.allowAdditionalArguments)
    assertEquals(
      AiStructuredToolArgumentType.STRING_ARRAY,
      request.tool.arguments.single { it.name == "tags" }.type,
    )
    assertTrue(request.systemInstruction.contains("通常テキストとしてJSONや説明文を返してはいけません"))
  }

  @Test
  fun `prompt exposes same series classifications and requires tool output`() {
    val prompt = buildLibraryOrganizationPrompt(
      book = testBook(),
      existingTags = listOf("一般タグ"),
      existingCollections = listOf("一般コレクション"),
      seriesContext = LibraryOrganizationSeriesContext(
        tagNames = listOf("シリーズ共通タグ"),
        collectionNames = listOf("シリーズ棚"),
      ),
    )

    assertTrue(prompt.contains("同一シリーズの確定済み分類"))
    assertTrue(prompt.contains("シリーズ共通タグ"))
    assertTrue(prompt.contains("シリーズ棚"))
    assertTrue(prompt.contains("submit_library_organization"))
    assertFalse(prompt.contains("JSON Schema:"))
  }
}

private class FakeTextInference(
  promptBudgetChars: Int,
) : AiTextInference {
  override val progress: Flow<AiTextInferenceProgress?> = flowOf(null)
  var generateCalls = 0

  private val model = AiTextInferenceModel(
    id = "test-model",
    name = "Test model",
    contextTokens = 8_192,
    maxInputChars = 16_000,
    promptBudgetChars = promptBudgetChars,
    cacheVariant = "test-variant",
  )

  override fun selectedModel(): AiTextInferenceModel = model

  override fun countTokens(text: String): Int = text.length

  override suspend fun generate(prompt: String): String {
    generateCalls += 1
    error("free-form generation must not be used for library organization")
  }
}

private class FakeStructuredTextInference(
  private val response: AiStructuredToolCall?,
) : AiStructuredTextInference {
  data class Request(
    val systemInstruction: String,
    val userMessage: String,
    val tool: AiStructuredTool,
  )

  val requests = mutableListOf<Request>()

  override suspend fun generateToolCall(
    systemInstruction: String,
    userMessage: String,
    tool: AiStructuredTool,
  ): AiStructuredToolCall? {
    requests += Request(systemInstruction, userMessage, tool)
    return response
  }
}

private fun testBook(): LibraryBook = LibraryBook(
  source = LibrarySource.KINDLE,
  sourceId = "TESTBOOK01",
  title = "Test Book",
  authors = listOf("Test Author"),
  publisher = null,
  publishedDate = null,
  description = null,
  isbn10 = null,
  isbn13 = null,
  thumbnailUrl = null,
  infoUrl = null,
  series = LibrarySeries(
    name = "Test Series",
    position = 2,
    id = "series-test",
  ),
)
