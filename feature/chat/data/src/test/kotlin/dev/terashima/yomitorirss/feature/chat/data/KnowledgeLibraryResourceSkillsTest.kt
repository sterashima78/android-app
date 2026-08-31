package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageSummary
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeReader
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeSource
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryReader
import dev.terashima.yomitorirss.feature.library.LibrarySnapshot
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeLibraryResourceSkillsTest {
  @Test
  fun `Knowledgeは候補検索後に必要なページだけ本文を取得する`() = runBlocking {
    val page = KnowledgePage(
      id = "knowledge-1",
      title = "Android memory調査",
      bodyMarkdown = "# 詳細\n端末内AIのmemory利用を整理した本文。",
      sourceCount = 1,
      generatedAt = "2026-08-31T00:00:00Z",
      editorManaged = false,
      sources = listOf(
        KnowledgeSource(
          citationNumber = 1,
          articleId = "article-1",
          title = "Android AI",
          url = "https://example.com/android-ai",
          sourceTitle = "Example",
          savedAt = "2026-08-30T00:00:00Z",
        ),
      ),
    )
    val knowledge = FakeKnowledgeReader(page)
    val skills = createKnowledgeLibraryResourceSkills(knowledge, FakeLibraryReader(emptyList()))
    val skill = skills.first { it.name == "knowledge-reader" }

    val candidates = skill.tools.first { it.definition.name == "search_knowledge_pages" }
      .execute(mapOf("query" to "memory"))

    assertEquals("memory", knowledge.lastQuery)
    assertTrue(candidates.contains("title=Android memory調査"))
    assertFalse(candidates.contains("端末内AIのmemory利用を整理した本文"))

    val detail = skill.tools.first { it.definition.name == "get_knowledge_page_detail" }
      .execute(mapOf("page_id" to "knowledge-1"))

    assertTrue(detail.contains("端末内AIのmemory利用を整理した本文"))
    assertTrue(detail.contains("citation=1"))
  }

  @Test
  fun `Libraryは複数語を異なる書誌項目へ一致させて候補を絞る`() = runBlocking {
    val matching = libraryBook(
      sourceId = "book-1",
      title = "Android AI実践",
      description = "端末上のnative memory最適化を扱う。",
    )
    val missingTerm = libraryBook(
      sourceId = "book-2",
      title = "Android UI入門",
      description = "Composeの画面設計を扱う。",
    )
    val skills = createKnowledgeLibraryResourceSkills(
      FakeKnowledgeReader(null),
      FakeLibraryReader(listOf(missingTerm, matching)),
    )
    val skill = skills.first { it.name == "library-reader" }

    val candidates = skill.tools.first { it.definition.name == "search_library_books" }
      .execute(mapOf("query" to "Android memory"))

    assertTrue(candidates.contains("source_id=book-1"))
    assertFalse(candidates.contains("source_id=book-2"))

    val detail = skill.tools.first { it.definition.name == "get_library_book_detail" }
      .execute(mapOf("source" to "kindle", "source_id" to "book-1"))

    assertTrue(detail.contains("description=端末上のnative memory最適化を扱う。"))
  }

  @Test
  fun `KnowledgeとLibraryは独立したread only skillとして公開する`() {
    val skills = createKnowledgeLibraryResourceSkills(
      FakeKnowledgeReader(null),
      FakeLibraryReader(emptyList()),
    )

    assertEquals(listOf("knowledge-reader", "library-reader"), skills.map { it.name })
  }

  private fun libraryBook(
    sourceId: String,
    title: String,
    description: String,
  ): LibraryBook = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = sourceId,
    title = title,
    authors = listOf("Example Author"),
    publisher = "Example Publisher",
    publishedDate = "2026-01-01",
    description = description,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private class FakeKnowledgeReader(
    private val page: KnowledgePage?,
  ) : KnowledgeReader {
    override val changes: StateFlow<Long> = MutableStateFlow(0L)
    var lastQuery: String? = null

    override suspend fun listPages(query: String): List<KnowledgePageSummary> {
      lastQuery = query
      val value = page ?: return emptyList()
      return listOf(
        KnowledgePageSummary(
          id = value.id,
          title = value.title,
          sourceCount = value.sourceCount,
          generatedAt = value.generatedAt,
          editorManaged = value.editorManaged,
        ),
      )
    }

    override suspend fun findPage(id: String): KnowledgePage? = page?.takeIf { it.id == id }
  }

  private class FakeLibraryReader(
    private val books: List<LibraryBook>,
  ) : LibraryReader {
    override suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(
      books = books,
      hiddenBooks = emptyList(),
      sourceStates = emptyMap(),
    )
  }
}
