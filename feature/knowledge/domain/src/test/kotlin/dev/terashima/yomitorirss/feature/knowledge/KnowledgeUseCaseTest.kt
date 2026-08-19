package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeUseCaseTest {
  @Test
  fun `build UseCaseは生成capabilityへ委譲する`() = runBlocking {
    val expected = KnowledgeBuildResult(generated = 1, reused = 2, pending = 3, skippedWithoutSummary = 4)
    val useCase = BuildKnowledgeUseCase(
      object : KnowledgeBuilder {
        override suspend fun rebuild(): KnowledgeBuildResult = expected
      },
    )

    assertEquals(expected, useCase())
  }

  @Test
  fun `create UseCaseは生成capabilityへ依頼を渡す`() = runBlocking {
    val expected = page("created")
    var received: Pair<String, String?>? = null
    val useCase = CreateKnowledgePageUseCase(
      object : KnowledgePageCreator {
        override suspend fun createPage(request: String, sourcePageId: String?): KnowledgePage {
          received = request to sourcePageId
          return expected
        }
      },
    )

    assertEquals(expected, useCase("依頼", "source"))
    assertEquals("依頼" to "source", received)
  }

  @Test
  fun `edit UseCaseは生成capabilityへ編集内容を渡す`() = runBlocking {
    val expected = page("edited")
    var received: Pair<String, String>? = null
    val useCase = EditKnowledgePageUseCase(
      object : KnowledgePageEditor {
        override suspend fun editPage(id: String, instruction: String): KnowledgePage {
          received = id to instruction
          return expected
        }
      },
    )

    assertEquals(expected, useCase("page-1", "編集"))
    assertEquals("page-1" to "編集", received)
  }

  private fun page(title: String) = KnowledgePage(
    id = "page-1",
    title = title,
    bodyMarkdown = "本文",
    sourceCount = 0,
    generatedAt = "2026-08-19T00:00:00Z",
    editorManaged = true,
    sources = emptyList(),
  )
}
