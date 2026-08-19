package dev.terashima.yomitorirss.feature.summary

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkAutoEnrichmentUseCaseTest {
  @Test
  fun `通常記事のBookmark追加はenrichmentを要求する`() = runBlocking {
    val requester = RecordingEnrichmentRequester()
    val useCase = BookmarkAutoEnrichmentUseCase(
      articleRepository = FakeArticleRepository(article()),
      enrichmentRequester = requester,
    )

    useCase("article-1")

    assertEquals(listOf("article-1"), requester.requestedIds)
  }

  @Test
  fun `対象外Contentはenrichmentを要求しない`() = runBlocking {
    val requester = RecordingEnrichmentRequester()
    val useCase = BookmarkAutoEnrichmentUseCase(
      articleRepository = FakeArticleRepository(article(contentType = ContentType.COMIC)),
      enrichmentRequester = requester,
    )

    useCase("article-1")

    assertTrue(requester.requestedIds.isEmpty())
  }

  @Test
  fun `通常のRSS記事は自動AI処理の対象にする`() {
    assertTrue(
      shouldRequestBookmarkEnrichment(
        url = "https://example.com/articles/1",
        sourceFeedUrl = "https://example.com/feed.xml",
        contentType = ContentType.ARTICLE,
      ),
    )
  }

  @Test
  fun `漫画は自動AI処理の対象外にする`() {
    assertFalse(
      shouldRequestBookmarkEnrichment(
        url = "https://example.com/comics/1",
        sourceFeedUrl = "https://example.com/comics/feed.xml",
        contentType = ContentType.COMIC,
      ),
    )
  }

  @Test
  fun `RedditとYouTubeは自動AI処理の対象外にする`() {
    assertFalse(
      shouldRequestBookmarkEnrichment(
        url = "https://www.reddit.com/r/android/comments/abc123/example/",
        sourceFeedUrl = "https://www.reddit.com/r/android/new/.rss",
      ),
    )
    assertFalse(
      shouldRequestBookmarkEnrichment(
        url = "https://www.youtube.com/watch?v=abcdefghijk",
        sourceFeedUrl = "",
      ),
    )
  }

  private fun article(contentType: ContentType = ContentType.ARTICLE) = Article(
    id = "article-1",
    feedId = "feed-1",
    externalId = null,
    identityKey = "identity-1",
    url = "https://example.com/articles/1",
    title = "Article",
    publishedAt = "2026-08-19T00:00:00Z",
    fetchedAt = "2026-08-19T00:00:00Z",
    readAt = null,
    sourceTitle = "Example",
    sourceFeedUrl = "https://example.com/feed.xml",
    effectiveContentType = contentType,
  )

  private class FakeArticleRepository(
    private val article: Article?,
  ) : ArticleRepository {
    override val changes: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun cleanupExpiredArticles() = Unit
    override suspend fun findArticle(articleId: String): Article? = article?.takeIf { it.id == articleId }
    override suspend fun findArticles(articleIds: Collection<String>): List<Article> =
      article?.takeIf { it.id in articleIds }?.let(::listOf).orEmpty()
    override suspend fun listUnreadArticles(): List<Article> = emptyList()
    override suspend fun listHistoryArticles(): List<Article> = emptyList()
    override suspend fun markArticleRead(articleId: String) = Unit
    override suspend fun markArticleUnread(articleId: String) = Unit
    override suspend fun markAllUnreadAsRead(): Int = 0
    override suspend fun setArticleContentType(articleId: String, contentType: ContentType?) = Unit
  }

  private class RecordingEnrichmentRequester : BookmarkEnrichmentRequester {
    val requestedIds = mutableListOf<String>()

    override suspend fun requestBookmarkEnrichment(articleId: String): SummaryRequestResult {
      requestedIds += articleId
      return SummaryRequestResult.Enqueued(accepted = true, forceRefresh = false)
    }

    override suspend fun enqueueMissingBookmarkEnrichment(articleIds: List<String>): Int = articleIds.size
  }
}
