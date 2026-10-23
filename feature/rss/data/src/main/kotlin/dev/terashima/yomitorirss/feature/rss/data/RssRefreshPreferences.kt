package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import dev.terashima.yomitorirss.feature.rss.FeedRefreshIntervals
import dev.terashima.yomitorirss.feature.rss.RSS_REFRESH_INTERVAL_OPTIONS

/** RSS-owned user preferences for automatic feed refresh. */
class RssRefreshPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences("rss_refresh", Context.MODE_PRIVATE)

  var screenRefreshIntervalMinutes: Long
    get() = preferences.getLong(KEY_SCREEN_INTERVAL, DEFAULT_SCREEN_INTERVAL)
      .takeIf(SCREEN_INTERVAL_OPTIONS::contains) ?: DEFAULT_SCREEN_INTERVAL
    set(value) {
      require(value in SCREEN_INTERVAL_OPTIONS)
      preferences.edit().putLong(KEY_SCREEN_INTERVAL, value).apply()
    }

  var failureRetryIntervalMinutes: Long
    get() = preferences.getLong(KEY_FAILURE_INTERVAL, DEFAULT_FAILURE_INTERVAL)
      .takeIf(FAILURE_INTERVAL_OPTIONS::contains) ?: DEFAULT_FAILURE_INTERVAL
    set(value) {
      require(value in FAILURE_INTERVAL_OPTIONS)
      preferences.edit().putLong(KEY_FAILURE_INTERVAL, value).apply()
    }

  fun intervals(): FeedRefreshIntervals = FeedRefreshIntervals(
    automaticMinutes = screenRefreshIntervalMinutes,
    failureRetryMinutes = failureRetryIntervalMinutes,
  )

  companion object {
    val SCREEN_INTERVAL_OPTIONS = RSS_REFRESH_INTERVAL_OPTIONS
    val FAILURE_INTERVAL_OPTIONS = listOf(1L, 5L, 15L, 30L, 60L)
    const val DEFAULT_SCREEN_INTERVAL = 30L
    const val DEFAULT_FAILURE_INTERVAL = 5L
    private const val KEY_SCREEN_INTERVAL = "screen_refresh_interval_minutes"
    private const val KEY_FAILURE_INTERVAL = "failure_retry_interval_minutes"
  }
}
