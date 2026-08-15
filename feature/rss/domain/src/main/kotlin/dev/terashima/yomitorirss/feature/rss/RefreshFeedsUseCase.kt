package dev.terashima.yomitorirss.feature.rss

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RefreshFeedsResult(
  val total: Int,
  val failures: Int,
)

class RefreshFeedsUseCase(
  private val repository: FeedRepository,
  private val maxConcurrency: Int = 4,
) {
  suspend operator fun invoke(
    feeds: List<Feed>,
    onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
  ): RefreshFeedsResult {
    val failures = AtomicInteger()
    val completed = AtomicInteger()
    val permits = Semaphore(maxConcurrency)

    coroutineScope {
      feeds.map { feed ->
        async {
          permits.withPermit {
            runCatching { repository.refreshFeed(feed) }
              .onFailure { failures.incrementAndGet() }
            onProgress(completed.incrementAndGet(), feeds.size)
          }
        }
      }.awaitAll()
    }

    return RefreshFeedsResult(
      total = feeds.size,
      failures = failures.get(),
    )
  }
}
