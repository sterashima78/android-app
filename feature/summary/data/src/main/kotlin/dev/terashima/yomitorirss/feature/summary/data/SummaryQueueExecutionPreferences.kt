package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context

internal class SummaryQueueExecutionPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  var paused: Boolean
    get() = preferences.getBoolean(KEY_PAUSED, false)
    set(value) {
      preferences.edit().putBoolean(KEY_PAUSED, value).apply()
    }

  var resumeWhenCharging: Boolean
    get() = preferences.getBoolean(KEY_RESUME_WHEN_CHARGING, true)
    set(value) {
      preferences.edit().putBoolean(KEY_RESUME_WHEN_CHARGING, value).apply()
    }

  companion object {
    internal const val PREFERENCES_NAME = "summary_queue_execution"
    private const val KEY_PAUSED = "paused"
    private const val KEY_RESUME_WHEN_CHARGING = "resume_when_charging"
  }
}
