package dev.terashima.yomitorirss.core.background

import android.content.Context

/**
 * Cross-feature execution policy for background work that uses the shared local AI runtime.
 *
 * Feature-specific task state stays in each feature. This class only stores whether local-AI
 * background execution is globally paused and whether a paused queue may resume on charging.
 */
class LocalAiBackgroundExecutionPreferences(context: Context) {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  init {
    migrateSummaryExecutionPreferencesIfNeeded()
  }

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

  private fun migrateSummaryExecutionPreferencesIfNeeded() {
    if (preferences.contains(KEY_MIGRATED_FROM_SUMMARY)) return

    val legacy = appContext.getSharedPreferences(
      LEGACY_SUMMARY_PREFERENCES_NAME,
      Context.MODE_PRIVATE,
    )
    val editor = preferences.edit()
    if (legacy.contains(KEY_PAUSED)) {
      editor.putBoolean(KEY_PAUSED, legacy.getBoolean(KEY_PAUSED, false))
    }
    if (legacy.contains(KEY_RESUME_WHEN_CHARGING)) {
      editor.putBoolean(
        KEY_RESUME_WHEN_CHARGING,
        legacy.getBoolean(KEY_RESUME_WHEN_CHARGING, true),
      )
    }
    editor.putBoolean(KEY_MIGRATED_FROM_SUMMARY, true).apply()
  }

  companion object {
    const val PREFERENCES_NAME = "local_ai_background_execution"
    internal const val LEGACY_SUMMARY_PREFERENCES_NAME = "summary_queue_execution"
    internal const val KEY_PAUSED = "paused"
    internal const val KEY_RESUME_WHEN_CHARGING = "resume_when_charging"
    private const val KEY_MIGRATED_FROM_SUMMARY = "migrated_from_summary_queue_execution"
  }
}
