package dev.terashima.yomitorirss.core.background

import android.content.Context

/**
 * Cross-feature execution policy for background work that uses the shared local AI runtime.
 *
 * Feature-specific task state stays in each feature. This class only stores whether local-AI
 * background execution is globally paused and whether a paused queue may resume on charging.
 */
class LocalAiBackgroundExecutionPreferences(context: Context) {
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
    const val PREFERENCES_NAME = "local_ai_background_execution"
    internal const val KEY_PAUSED = "paused"
    internal const val KEY_RESUME_WHEN_CHARGING = "resume_when_charging"
  }
}
