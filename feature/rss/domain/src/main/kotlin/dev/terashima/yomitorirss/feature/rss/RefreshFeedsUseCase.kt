package dev.terashima.yomitorirss.feature.rss

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Automatic requests respect both intervals; user-initiated refreshes bypass both. */
data class FeedRefreshIntervals(
  val automaticMinutes: Long = 30L,
  val failureRetryMinutes: Long = 5L,
)

enum class FeedRefreshMode { AUTOMATIC, FORCE }

data class RefreshFeedsResult(
  val total: Int,
  val failures: Int,
  val skipped: Int = 0,
)

class RefreshFeedsUseCase(
  private val repository: FeedRepository,
  private val maxConcurrency: Int = 4,
  private val intervals: () -> FeedRefreshIntervals = { FeedRefreshIntervals() },
) {
  suspend operator fun invoke(
    feeds: List<Feed>,
    onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    mode: FeedRefreshMode = FeedRefreshMode.FORCE,
  ): RefreshFeedsResult {
    val failures = AtomicInteger()
    val skipped = AtomicInteger()
    val completed = AtomicInteger()
    val permits = Semaphore(maxConcurrency)
    val policy = intervals()

    coroutineScope {
      feeds.map { feed ->
        async {
          permits.withPermit {
            try {
              val fetched = if (mode == FeedRefreshMode.AUTOMATIC) {
                repository.refreshFeedIfDue(
                  feed,
                  minimumAgeMillis = policy.automaticMinutes * 60_000L,
                  failureRetryMillis = policy.failureRetryMinutes * 60_000L,
                )
              } else {
                repository.refreshFeed(feed)
                true
              }
              if (!fetched) skipped.incrementAndGet()
            } catch (error: CancellationException) {
              throw error
            } catch (_: Exception) {
              failures.incrementAndGet()
            }
            onProgress(completed.incrementAndGet(), feeds.size)
          }
        }
      }.awaitAll()
    }

    return RefreshFeedsResult(total = feeds.size, failures = failures.get(), skipped = skipped.get())
  }
}
