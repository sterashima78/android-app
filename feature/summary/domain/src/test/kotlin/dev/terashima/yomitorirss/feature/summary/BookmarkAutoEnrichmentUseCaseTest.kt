package dev.terashima.yomitorirss.feature.summary

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.bookmark.BookmarkReader
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
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
  fun `一括再実行は自動AI処理対象だけをrefreshへ渡す`() = runBlocking {
    val batchRequester = RecordingBatchRequester()
    val useCase = ReprocessBookmarkAutoEnrichmentUseCase(
      bookmarks = FakeBookmarkReader(
        listOf(
          BookmarkedArticle(article(id = "article-1"), savedAt = "now"),
          BookmarkedArticle(
            article(
              id = "reddit-1",
              url = "https://www.reddit.com/r/android/comments/abc123/example/",
              sourceFeedUrl = "https://www.reddit.com/r/android/new/.rss",
            ),
            savedAt = "now",
          ),
          BookmarkedArticle(
            article(id = "comic-1", contentType = ContentType.COMIC),
            savedAt = "now",
          ),
        ),
      ),
      batchRequester = batchRequester,
    )

    val count = useCase()

    assertEquals(1, count)
    assertEquals(listOf("article-1"), batchRequester.refreshedIds)
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

  private fun article(
    id: String = "article-1",
    url: String = "https://example.com/articles/1",
    sourceFeedUrl: String = "https://example.com/feed.xml",
    contentType: ContentType = ContentType.ARTICLE,
  ) = Article(
    id = id,
    feedId = "feed-1",
    externalId = null,
    identityKey = "identity-$id",
    url = url,
    title = "Article",
    publishedAt = "2026-08-19T00:00:00Z",
    fetchedAt = "2026-08-19T00:00:00Z",
    readAt = null,
    sourceTitle = "Example",
    sourceFeedUrl = sourceFeedUrl,
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

  private class FakeBookmarkReader(
    private val bookmarks: List<BookmarkedArticle>,
  ) : BookmarkReader {
    override val changes: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> = bookmarks
    override suspend fun listAllSavedArticles(): List<BookmarkedArticle> = bookmarks
    override suspend fun listReadLaterArticles(): List<BookmarkedArticle> = emptyList()
    override suspend fun isBookmarked(articleId: String): Boolean = bookmarks.any { it.article.id == articleId }
  }

  private class RecordingEnrichmentRequester : BookmarkEnrichmentRequester {
    val requestedIds = mutableListOf<String>()

    override suspend fun requestBookmarkEnrichment(articleId: String): SummaryRequestResult {
      requestedIds += articleId
      return SummaryRequestResult.Enqueued(accepted = true, forceRefresh = false)
    }
  }

  private class RecordingBatchRequester : BookmarkEnrichmentBatchRequester {
    var refreshedIds: List<String> = emptyList()

    override suspend fun enqueueMissingBookmarkEnrichment(articleIds: List<String>): Int = articleIds.size

    override suspend fun enqueueBookmarkEnrichmentRefresh(articleIds: List<String>): Int {
      refreshedIds = articleIds
      return articleIds.size
    }
  }
}
