package dev.terashima.yomitorirss.feature.reddit

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RedditViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `初期読込では対象記事だけを状態へ投影する`() = runTest(dispatcher) {
    val articleRepository = FakeArticleRepository(
      unread = listOf(article("reddit", "https://www.reddit.com/r/androiddev/new/.rss"), article("other", "https://example.invalid/feed")),
    )
    val viewModel = RedditViewModel(
      redditRepository = FakeRedditRepository(),
      articleRepository = articleRepository,
      bookmarkRepository = FakeBookmarkRepository(),
      backgroundDispatcher = dispatcher,
    )

    advanceUntilIdle()

    assertEquals(listOf("reddit"), viewModel.state.value.unread.map { it.id })
    assertFalse(viewModel.state.value.refreshing)
  }

  @Test
  fun `購読がない状態で手動更新すると説明messageを表示する`() = runTest(dispatcher) {
    val viewModel = RedditViewModel(
      redditRepository = FakeRedditRepository(),
      articleRepository = FakeArticleRepository(),
      bookmarkRepository = FakeBookmarkRepository(),
      backgroundDispatcher = dispatcher,
    )
    advanceUntilIdle()

    viewModel.refresh()
    advanceUntilIdle()

    assertEquals("Redditの購読はありません", viewModel.state.value.message)
    assertFalse(viewModel.state.value.refreshing)
  }

  private fun article(id: String, feedUrl: String) = Article(
    id = id,
    feedId = id,
    externalId = null,
    identityKey = id,
    url = "https://example.invalid/$id",
    title = id,
    publishedAt = "2026-01-01T00:00:00Z",
    fetchedAt = "2026-01-01T00:00:00Z",
    readAt = null,
    sourceTitle = id,
    sourceFeedUrl = feedUrl,
  )
}

private class FakeRedditRepository : RedditRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  override suspend fun listSubscriptions(): List<RedditSubscription> = emptyList()
  override suspend fun addCommunity(input: String) = Unit
  override suspend fun subscribeThread(articleUrl: String) = Unit
  override suspend fun unsubscribeThread(articleUrl: String) = Unit
  override suspend fun deleteSubscription(subscriptionId: String) = Unit
  override suspend fun refreshAll(onProgress: (Int, Int) -> Unit) = RedditRefreshResult(0, 0)
}

private class FakeArticleRepository(
  private val unread: List<Article> = emptyList(),
  private val history: List<Article> = emptyList(),
) : ArticleRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  override suspend fun cleanupExpiredArticles() = Unit
  override suspend fun findArticle(articleId: String): Article? = null
  override suspend fun findArticles(articleIds: Collection<String>): List<Article> = emptyList()
  override suspend fun listUnreadArticles(): List<Article> = unread
  override suspend fun listHistoryArticles(): List<Article> = history
  override suspend fun markArticleRead(articleId: String) = Unit
  override suspend fun markArticleUnread(articleId: String) = Unit
  override suspend fun markAllUnreadAsRead(): Int = 0
  override suspend fun setArticleContentType(articleId: String, contentType: ContentType?) = Unit
}

private class FakeBookmarkRepository : BookmarkRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> = emptyList()
  override suspend fun listReadLaterArticles(): List<BookmarkedArticle> = emptyList()
  override suspend fun isBookmarked(articleId: String): Boolean = false
  override suspend fun listFolders(): List<BookmarkFolder> = emptyList()
  override suspend fun listTags(): List<Tag> = emptyList()
  override suspend fun createFolder(name: String) = Unit
  override suspend fun renameFolder(folderId: String, name: String) = Unit
  override suspend fun deleteFolder(folderId: String) = Unit
  override suspend fun createTag(name: String) = Unit
  override suspend fun renameTag(tagId: String, name: String) = Unit
  override suspend fun deleteTag(tagId: String) = Unit
  override suspend fun deleteUnusedTags(): Int = 0
  override suspend fun moveArticleToFolder(articleId: String, folderId: String?) = Unit
  override suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>) = Unit
  override suspend fun saveAndReadArticle(articleId: String) = Unit
  override suspend fun markReadLater(articleId: String) = Unit
  override suspend fun unsaveArticle(articleId: String) = Unit
  override suspend fun removeReadLater(articleId: String) = Unit
  override suspend fun restoreReadLater(articleId: String, tags: Set<Tag>) = Unit
  override suspend fun saveSharedArticle(url: String, title: String, sourceTitle: String) = BookmarkSaveResult.ADDED
  override suspend fun saveSharedArticleToFolder(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String,
  ) = BookmarkSaveResult.ADDED
}
