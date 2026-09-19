package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.ContentType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshFeedsUseCaseTest {
  @Test
  fun `失敗したFeedがあっても全件を処理し進捗と失敗数を返す`() = runBlocking {
    val repository = RecordingFeedRepository(failingFeedIds = setOf("2"))
    val progress = mutableListOf<Pair<Int, Int>>()
    val useCase = RefreshFeedsUseCase(repository, maxConcurrency = 2)

    val result = useCase(
      feeds = listOf(feed("1"), feed("2"), feed("3")),
      onProgress = { completed, total -> progress += completed to total },
    )

    assertEquals(RefreshFeedsResult(total = 3, failures = 1), result)
    assertEquals(setOf("1", "2", "3"), repository.refreshedFeedIds.toSet())
    assertEquals(listOf(1, 2, 3), progress.map(Pair<Int, Int>::first).sorted())
    assertTrue(progress.all { it.second == 3 })
  }

  @Test
  fun `同時refresh数をmaxConcurrency以下に制限する`() = runBlocking {
    val repository = RecordingFeedRepository(delayMillis = 40)
    val useCase = RefreshFeedsUseCase(repository, maxConcurrency = 2)

    useCase(listOf(feed("1"), feed("2"), feed("3"), feed("4")))

    assertEquals(2, repository.maxObservedConcurrency)
  }

  private fun feed(id: String) = Feed(
    id = id,
    title = "feed-$id",
    feedUrl = "https://example.invalid/$id.xml",
    siteUrl = null,
    etag = null,
    lastModified = null,
    lastFetchedAt = null,
    lastError = null,
    createdAt = "2026-01-01T00:00:00Z",
  )
}

private class RecordingFeedRepository(
  private val failingFeedIds: Set<String> = emptySet(),
  private val delayMillis: Long = 0,
) : FeedRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  val refreshedFeedIds = mutableListOf<String>()

  private val activeRefreshes = AtomicInteger()
  private val peakRefreshes = AtomicInteger()
  val maxObservedConcurrency: Int get() = peakRefreshes.get()

  override suspend fun refreshFeed(feed: Feed) {
    val active = activeRefreshes.incrementAndGet()
    peakRefreshes.updateAndGet { current -> maxOf(current, active) }
    try {
      if (delayMillis > 0) delay(delayMillis)
      refreshedFeedIds += feed.id
      if (feed.id in failingFeedIds) error("refresh failed")
    } finally {
      activeRefreshes.decrementAndGet()
    }
  }

  override suspend fun listFeeds(): List<Feed> = emptyList()
  override suspend fun listFolders(): List<FeedFolder> = emptyList()
  override suspend fun inspect(input: String): FeedInspection = FeedInspection()
  override suspend fun addFeed(url: String, markExistingArticlesRead: Boolean) = Unit
  override suspend fun renameFeed(feedId: String, name: String) = Unit
  override suspend fun deleteFeed(feedId: String) = Unit
  override suspend fun createFolder(name: String) = Unit
  override suspend fun renameFolder(folderId: String, name: String) = Unit
  override suspend fun deleteFolder(folderId: String) = Unit
  override suspend fun moveFeedToFolder(feedId: String, folderId: String?) = Unit
  override suspend fun setFeedContentType(feedId: String, contentType: ContentType?) = Unit
  override suspend fun setFolderContentType(folderId: String, contentType: ContentType?) = Unit
}
