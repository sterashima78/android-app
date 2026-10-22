package dev.terashima.yomitorirss.feature.rss

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class FeedRefreshOrigin(val diagnosticCode: String) {
  SCREEN_OPEN("screen_open"),
  MANUAL("manual"),
  BACKGROUND("background"),
  IMPORT("import"),
}

class FeedRefreshContext(val origin: FeedRefreshOrigin) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<FeedRefreshContext>
}

/** Automatic requests respect both intervals; user-initiated refreshes bypass both. */
data class FeedRefreshIntervals(
  val automaticMinutes: Long = 30L,
  val failureRetryMinutes: Long = 5L,
  val scheduledMinutes: Long = 60L,
)

enum class FeedRefreshMode { AUTOMATIC, SCHEDULED, FORCE }

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
    origin: FeedRefreshOrigin = FeedRefreshOrigin.MANUAL,
    onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    mode: FeedRefreshMode = when (origin) {
      FeedRefreshOrigin.SCREEN_OPEN -> FeedRefreshMode.AUTOMATIC
      FeedRefreshOrigin.BACKGROUND -> FeedRefreshMode.SCHEDULED
      else -> FeedRefreshMode.FORCE
    },
  ): RefreshFeedsResult {
    val failures = AtomicInteger()
    val skipped = AtomicInteger()
    val completed = AtomicInteger()
    val permits = Semaphore(maxConcurrency)
    val policy = intervals()

    val effectiveOrigin = when (mode) {
      FeedRefreshMode.AUTOMATIC -> FeedRefreshOrigin.SCREEN_OPEN
      FeedRefreshMode.SCHEDULED -> FeedRefreshOrigin.BACKGROUND
      FeedRefreshMode.FORCE -> origin
    }
    withContext(FeedRefreshContext(effectiveOrigin)) {
      coroutineScope {
        feeds.map { feed ->
        async {
          permits.withPermit {
            try {
              val fetched = when (mode) {
                FeedRefreshMode.AUTOMATIC, FeedRefreshMode.SCHEDULED -> repository.refreshFeedIfDue(
                  feed,
                  minimumAgeMillis = (if (mode == FeedRefreshMode.AUTOMATIC) {
                    policy.automaticMinutes
                  } else {
                    policy.scheduledMinutes
                  }) * 60_000L,
                  failureRetryMillis = policy.failureRetryMinutes * 60_000L,
                )
                FeedRefreshMode.FORCE -> repository.refreshFeedIfDue(
                  feed,
                  minimumAgeMillis = 0L,
                  failureRetryMillis = 0L,
                )
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
    }

    return RefreshFeedsResult(total = feeds.size, failures = failures.get(), skipped = skipped.get())
  }
}
