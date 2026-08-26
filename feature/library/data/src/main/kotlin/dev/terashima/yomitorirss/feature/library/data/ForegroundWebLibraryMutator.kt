package dev.terashima.yomitorirss.feature.library.data

import android.app.Activity
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataRefreshResult
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ForegroundWebLibraryMutator(
  private val delegate: WebLibraryMutator,
  private val activityProvider: () -> Activity?,
  private val retryDelayMillis: Long = DEFAULT_FOREGROUND_RETRY_DELAY_MILLIS,
) : WebLibraryMutator {
  override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
    awaitForegroundActivity()
    return delegate.addWebBook(url, titleHint)
  }

  override suspend fun refreshWebBook(book: LibraryBook): LibraryBook {
    awaitForegroundActivity()
    return delegate.refreshWebBook(book)
  }

  override suspend fun refreshWebBookWithReport(book: LibraryBook): WebLibraryMetadataRefreshResult {
    awaitForegroundActivity()
    return delegate.refreshWebBookWithReport(book)
  }

  override suspend fun removeWebBook(book: LibraryBook) {
    delegate.removeWebBook(book)
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
  require(retryDelayMillis >= 0L)
  while (!isAvailable()) {
    delay(retryDelayMillis)
  }
}

private const val DEFAULT_FOREGROUND_RETRY_DELAY_MILLIS = 100L
