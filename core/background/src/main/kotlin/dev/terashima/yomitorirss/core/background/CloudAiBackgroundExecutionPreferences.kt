package dev.terashima.yomitorirss.core.background

import android.content.Context

/**
 * Cross-feature execution policy for background work that uses cloud AI providers.
 *
 * Feature-specific task state stays in each feature. This class only stores whether cloud-AI
 * background execution is globally paused. Cloud work is intentionally independent from the
 * local-AI charging policy.
 */
class CloudAiBackgroundExecutionPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  var paused: Boolean
    get() = preferences.getBoolean(KEY_PAUSED, false)
    set(value) {
      preferences.edit().putBoolean(KEY_PAUSED, value).apply()
    }

  companion object {
    const val PREFERENCES_NAME = "cloud_ai_background_execution"
    internal const val KEY_PAUSED = "paused"
  }
}
