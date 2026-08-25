package dev.terashima.yomitorirss.feature.library.data

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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLibraryOrganizationSuggesterTest {
  @Test
  fun `AI suggestion parser validates strict JSON and deduplicates labels`() {
    val suggestion = parseLibraryOrganizationSuggestion(
      """{"tags":["Android"," android ","設計"],"collections":["技術","技術"],"reason":"書誌情報から判断"}""",
    )

    assertEquals(listOf("Android", "設計"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals("書誌情報から判断", suggestion.reason)
  }

  @Test
  fun `AI suggestion parser rejects non array tags`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseLibraryOrganizationSuggestion(
        """{"tags":"「Android」「設計」","collections":["技術"],"reason":"test"}""",
      )
    }

    assertTrue(error.message.orEmpty().contains("tags は文字列配列"))
  }

  @Test
  fun `AI suggestion parser rejects values beyond schema limits`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseLibraryOrganizationSuggestion(
        """{"tags":["1","2","3","4","5","6"],"collections":["A"],"reason":"test"}""",
      )
    }

    assertTrue(error.message.orEmpty().contains("tags は最大 5 件"))
  }

  @Test
  fun `invalid AI output is regenerated once with schema error feedback`() = runBlocking {
    val outputs = ArrayDeque(
      listOf(
        """{"tags":"Android","collections":[],"reason":"invalid"}""",
        """{"tags":["Android"],"collections":["技術"],"reason":"valid"}""",
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
    assertTrue(prompts[1].contains("JSON Schema検証に失敗"))
    assertTrue(prompts[1].contains("tags は文字列配列"))
  }

  @Test
  fun `suggester uses provider neutral model budget and generation`() = runBlocking {
    val inference = FakeTextInference(
      response = """{"tags":["Android"],"collections":["技術"],"reason":"valid"}""",
      promptBudgetChars = 8_000,
    )

    val suggestion = DefaultLibraryOrganizationSuggester(inference).suggest(
      book = testBook(),
      existingTags = listOf("Android"),
      existingCollections = listOf("技術"),
      seriesContext = null,
    )

    assertEquals(listOf("Android"), suggestion.tagNames)
    assertEquals(listOf("技術"), suggestion.collectionNames)
    assertEquals(1, inference.generatedPrompts.size)
    assertTrue(inference.generatedPrompts.single().length <= 8_000)
  }

  @Test
  fun `prompt exposes same series classifications separately from global taxonomy`() {
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
    assertTrue(prompt.contains("additionalProperties"))
  }
}

private class FakeTextInference(
  private val response: String,
  promptBudgetChars: Int,
) : AiTextInference {
  override val progress: Flow<AiTextInferenceProgress?> = flowOf(null)
  val generatedPrompts = mutableListOf<String>()

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
    generatedPrompts += prompt
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
