package dev.terashima.yomitorirss.feature.bookmark

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBookmarksUseCaseTest {
  @Test
  fun `import はContent作成とCuration保存とタグ付与をapplication serviceで連携する`() = runBlocking {
    val source = FakeImportSource(
      BookmarkImportBatch(
        entries = listOf(
          BookmarkImportEntry(
            title = "First",
            url = "https://example.com/first",
            createdAt = "2026-08-01T00:00:00Z",
            sourceTitle = "example.com",
            tagNames = listOf("news", "android"),
          ),
          BookmarkImportEntry(
            title = "Second",
            url = "https://example.com/second",
            createdAt = "2026-08-02T00:00:00Z",
            sourceTitle = "example.com",
            tagNames = emptyList(),
          ),
        ),
        skipped = 3,
      ),
    )
    val gateway = FakeArticleGateway(ArrayDeque(listOf("article-1", "article-2")))
    val writer = RecordingImportWriter(ArrayDeque(listOf(true, false)))
    var changed = 0
    val useCase = ImportBookmarksUseCase(
      source = source,
      articleGateway = gateway,
      writer = writer,
      onChanged = { changed += 1 },
    )

    val result = useCase("content://bookmarks", BookmarkImportFormat.HTML)

    assertEquals(BookmarkImportResult(added = 1, duplicates = 1, skipped = 3), result)
    assertEquals(listOf("html", "html"), gateway.identityPrefixes)
    assertEquals(
      listOf(
        "article-1" to "2026-08-01T00:00:00Z",
        "article-2" to "2026-08-02T00:00:00Z",
      ),
      writer.saved,
    )
    assertEquals(
      listOf(
        "article-1" to listOf("news", "android"),
        "article-2" to emptyList(),
      ),
      writer.tags,
    )
    assertEquals(1, changed)
    assertEquals(listOf("content://bookmarks" to BookmarkImportFormat.HTML), source.calls)
  }

  @Test
  fun `解析に失敗した場合は変更通知を行わない`() = runBlocking {
    var changed = false
    val useCase = ImportBookmarksUseCase(
      source = object : BookmarkImportSource {
        override suspend fun read(documentUri: String, format: BookmarkImportFormat): BookmarkImportBatch {
          error("invalid document")
        }
      },
      articleGateway = FakeArticleGateway(ArrayDeque()),
      writer = RecordingImportWriter(ArrayDeque()),
      onChanged = { changed = true },
    )

    val failure = runCatching { useCase("content://invalid", BookmarkImportFormat.CSV) }

    assertTrue(failure.isFailure)
    assertEquals(false, changed)
  }
}

private class FakeImportSource(
  private val batch: BookmarkImportBatch,
) : BookmarkImportSource {
  val calls = mutableListOf<Pair<String, BookmarkImportFormat>>()

  override suspend fun read(documentUri: String, format: BookmarkImportFormat): BookmarkImportBatch {
    calls += documentUri to format
    return batch
  }
}

private class RecordingImportWriter(
  private val saveResults: ArrayDeque<Boolean>,
) : BookmarkImportWriter {
  val saved = mutableListOf<Pair<String, String>>()
  val tags = mutableListOf<Pair<String, List<String>>>()

  override suspend fun saveBookmark(articleId: String, savedAt: String): Boolean {
    saved += articleId to savedAt
    return saveResults.removeFirst()
  }

  override suspend fun addTags(articleId: String, tagNames: List<String>) {
    tags += articleId to tagNames
  }
}

private class FakeArticleGateway(
  private val articleIds: ArrayDeque<String>,
) : BookmarkArticleGateway {
  val identityPrefixes = mutableListOf<String>()

  override suspend fun markRead(articleId: String) = Unit

  override suspend fun findOrCreateSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): String = error("not used")

  override suspend fun findOrCreateImportedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): String {
    identityPrefixes += identityPrefix
    return articleIds.removeFirst()
  }
}
