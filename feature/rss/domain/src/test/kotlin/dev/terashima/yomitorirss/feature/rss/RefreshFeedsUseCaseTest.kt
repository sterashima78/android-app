package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.ContentType
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlin.coroutines.coroutineContext
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

  @Test
  fun `バックグラウンド起点は並列取得の各処理へ伝播する`() = runBlocking {
    val repository = RecordingFeedRepository()
    RefreshFeedsUseCase(repository, maxConcurrency = 2)(
      feeds = listOf(feed("1"), feed("2"), feed("3")),
      origin = FeedRefreshOrigin.BACKGROUND,
    )

    assertEquals(
      listOf(FeedRefreshOrigin.BACKGROUND, FeedRefreshOrigin.BACKGROUND, FeedRefreshOrigin.BACKGROUND),
      repository.origins.toList(),
    )
  }


  @Test
  fun `自動更新は設定された間隔を渡して取得不要のフィードを飛ばす`() = runBlocking {
    val repository = RecordingFeedRepository(skipFeedIds = setOf("1"))
    val progress = mutableListOf<Pair<Int, Int>>()
    val useCase = RefreshFeedsUseCase(
      repository,
      maxConcurrency = 1,
      intervals = { FeedRefreshIntervals(automaticMinutes = 60L, failureRetryMinutes = 15L) },
    )
    val result = useCase(
      listOf(feed("1"), feed("2")),
      onProgress = { completed, total -> progress += completed to total },
      mode = FeedRefreshMode.AUTOMATIC,
    )

    assertEquals(RefreshFeedsResult(total = 2, failures = 0, skipped = 1), result)
    assertEquals(listOf("2"), repository.refreshedFeedIds)
    assertEquals(listOf(3_600_000L to 900_000L, 3_600_000L to 900_000L), repository.policies)
    assertEquals(listOf(1 to 2, 2 to 2), progress)
  }

  @Test
  fun `手動更新は取得間隔を無視する`() = runBlocking {
    val repository = RecordingFeedRepository(skipFeedIds = setOf("1"))
    val useCase = RefreshFeedsUseCase(repository)
    val result = useCase(listOf(feed("1")), mode = FeedRefreshMode.FORCE)
    assertEquals(RefreshFeedsResult(total = 1, failures = 0), result)
    assertEquals(listOf("1"), repository.refreshedFeedIds)
    assertTrue(repository.policies.isEmpty())
  }

  @Test
  fun `キャンセルは個別フィードの失敗へ変換しない`() = runBlocking {
    val repository = RecordingFeedRepository(cancelFeedIds = setOf("1"))
    val useCase = RefreshFeedsUseCase(repository)
    var cancelled = false
    try {
      useCase(listOf(feed("1")))
    } catch (_: kotlinx.coroutines.CancellationException) {
      cancelled = true
    }
    assertTrue(cancelled)
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
  private val skipFeedIds: Set<String> = emptySet(),
  private val cancelFeedIds: Set<String> = emptySet(),
) : FeedRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  val refreshedFeedIds = mutableListOf<String>()
  val policies = mutableListOf<Pair<Long, Long>>()

  override suspend fun refreshFeedIfDue(
    feed: Feed,
    minimumAgeMillis: Long,
    failureRetryMillis: Long,
  ): Boolean {
    policies += minimumAgeMillis to failureRetryMillis
    if (feed.id in skipFeedIds) return false
    refreshFeed(feed)
    return true
  }

  val origins = Collections.synchronizedList(mutableListOf<FeedRefreshOrigin?>())

  private val activeRefreshes = AtomicInteger()
  private val peakRefreshes = AtomicInteger()
  val maxObservedConcurrency: Int get() = peakRefreshes.get()

  override suspend fun refreshFeed(feed: Feed) {
    origins += coroutineContext[FeedRefreshContext]?.origin
    if (feed.id in cancelFeedIds) throw kotlinx.coroutines.CancellationException("cancelled")
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
