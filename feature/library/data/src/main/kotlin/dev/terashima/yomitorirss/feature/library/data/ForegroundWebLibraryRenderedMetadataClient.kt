package dev.terashima.yomitorirss.feature.library.data

import android.app.Activity
import dev.terashima.yomitorirss.feature.library.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ForegroundWebLibraryRenderedMetadataClient(
  private val delegate: WebLibraryRenderedMetadataClient,
  private val activityProvider: () -> Activity?,
  private val retryDelayMillis: Long = DEFAULT_FOREGROUND_RETRY_DELAY_MILLIS,
) : WebLibraryRenderedMetadataClient {
  override fun hasCustomExtractor(url: String): Boolean = delegate.hasCustomExtractor(url)

  override suspend fun fetch(url: String, titleHint: String?): LibraryBook {
    awaitForegroundActivity()
    return delegate.fetch(url, titleHint)
  }

  override suspend fun fetchWithReport(
    url: String,
    titleHint: String?,
  ): WebLibraryRenderedMetadataFetchResult {
    awaitForegroundActivity()
    return delegate.fetchWithReport(url, titleHint)
  }

  private suspend fun awaitForegroundActivity() {
    awaitWebLibraryForegroundAvailability(
      retryDelayMillis = retryDelayMillis,
      isAvailable = {
        withContext(Dispatchers.Main.immediate) {
          activityProvider()?.let { activity ->
            !activity.isFinishing && !activity.isDestroyed
          } == true
        }
      },
    )
  }
}

internal suspend fun awaitWebLibraryForegroundAvailability(
  retryDelayMillis: Long = DEFAULT_FOREGROUND_RETRY_DELAY_MILLIS,
  isAvailable: suspend () -> Boolean,
) {
  require(retryDelayMillis > 0L)
  while (!isAvailable()) {
    delay(retryDelayMillis)
  }
}

private const val DEFAULT_FOREGROUND_RETRY_DELAY_MILLIS = 100L
